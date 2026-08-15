/*
 * Pipeline CI/CD - Plateforme_Demande_interne_STB_BANK
 *
 * Étapes : build + tests backend/frontend -> analyse qualité SonarQube (avec Quality Gate)
 * -> images Docker -> push registre -> déploiement Kubernetes (branche main uniquement).
 *
 * Prérequis Jenkins :
 *   - Plugins : Pipeline, Docker Pipeline, SonarQube Scanner, Kubernetes CLI, JUnit,
 *     Pipeline: Stage View.
 *   - Outil global "sonar-scanner" (Manage Jenkins > Tools) nommé "SonarScanner".
 *   - Serveur SonarQube déclaré dans Manage Jenkins > System sous le nom "SonarQube",
 *     avec un jeton stocké dans les Credentials sous l'identifiant "sonarqube-token".
 *   - Credentials "dockerhub-credentials" (Username/Password) pour le push d'images.
 *   - Credentials "kubeconfig" (Secret file) : kubeconfig du cluster cible.
 *   - Agent avec Docker, Maven/JDK 21 et Node 22 disponibles (ou utiliser les images
 *     Docker ci-dessous via `agent { docker { image ... } }` si l'agent Jenkins le permet).
 */

pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        DOCKERHUB_NAMESPACE = 'sirine5214'
        BACKEND_IMAGE       = "${DOCKERHUB_NAMESPACE}/stb-back-office"
        FRONTEND_IMAGE      = "${DOCKERHUB_NAMESPACE}/stb-front-office"
        IMAGE_TAG           = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'local'}"
        BACKEND_DIR         = 'Back_office'
        FRONTEND_DIR        = 'Front_office/template_STB/angular'
        K8S_DIR              = 'k8s'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend: Build & Unit Tests') {
            steps {
                dir(BACKEND_DIR) {
                    sh 'chmod +x ./mvnw && ./mvnw -B clean verify'
                }
            }
            post {
                always {
                    junit testResults: "${BACKEND_DIR}/target/surefire-reports/*.xml", allowEmptyResults: true
                }
            }
        }

        stage('Backend: SonarQube Analysis') {
            steps {
                dir(BACKEND_DIR) {
                    withSonarQubeEnv('SonarQube') {
                        withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                            sh '''
                                chmod +x ./mvnw
                                ./mvnw -B sonar:sonar \
                                  -Dsonar.projectKey=stb-bank-back-office \
                                  -Dsonar.projectName="STB Bank - Back Office" \
                                  -Dsonar.token=$SONAR_TOKEN \
                                  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                            '''
                        }
                    }
                }
            }
        }

        stage('Frontend: Install & Build') {
            steps {
                dir(FRONTEND_DIR) {
                    sh '''
                        npm ci
                        npx ng build --configuration production
                    '''
                }
            }
        }

        stage('Frontend: SonarQube Analysis') {
            steps {
                dir(FRONTEND_DIR) {
                    withSonarQubeEnv('SonarQube') {
                        withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                            script {
                                def scannerHome = tool 'SonarScanner'
                                sh "${scannerHome}/bin/sonar-scanner -Dsonar.token=$SONAR_TOKEN"
                            }
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                // Attend le verdict calculé côté serveur SonarQube pour les deux analyses ci-dessus.
                // Nécessite un webhook SonarQube pointant vers <jenkins_url>/sonarqube-webhook/.
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker: Build Images') {
            steps {
                dir(BACKEND_DIR) {
                    sh "docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} -t ${BACKEND_IMAGE}:latest ."
                }
                dir(FRONTEND_DIR) {
                    sh "docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -t ${FRONTEND_IMAGE}:latest ."
                }
            }
        }

        stage('Docker: Push Images') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                                   usernameVariable: 'DOCKER_USER',
                                                   passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push "$BACKEND_IMAGE:$IMAGE_TAG"
                        docker push "$BACKEND_IMAGE:latest"
                        docker push "$FRONTEND_IMAGE:$IMAGE_TAG"
                        docker push "$FRONTEND_IMAGE:latest"
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy: Kubernetes') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl apply -f ${K8S_DIR}/namespace.yaml
                        kubectl apply -f ${K8S_DIR}/ -n stb-bank
                        kubectl set image deployment/backend backend=${BACKEND_IMAGE}:${IMAGE_TAG} -n stb-bank
                        kubectl set image deployment/frontend frontend=${FRONTEND_IMAGE}:${IMAGE_TAG} -n stb-bank
                        kubectl rollout status deployment/backend -n stb-bank --timeout=180s
                        kubectl rollout status deployment/frontend -n stb-bank --timeout=180s
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
        }
        success {
            echo "Build ${env.BUILD_NUMBER} réussi - images taguées ${IMAGE_TAG}"
        }
        failure {
            echo "Build ${env.BUILD_NUMBER} en échec - voir les logs des stages ci-dessus"
        }
    }
}
