/*
 * Pipeline CI/CD - Plateforme_Demande_interne_STB_BANK
 *
 * Étapes : build + tests backend/frontend -> analyse qualité SonarQube -> images Docker
 * -> push registre -> déploiement Kubernetes (branche main uniquement).
 *
 * Chaque étape optionnelle (SonarQube, push Docker, déploiement k8s) est protégée par
 * catchError : si le credential ou l'outil correspondant n'est pas encore configuré, le
 * pipeline continue en state "UNSTABLE" plutôt que d'échouer complètement. Ça permet de
 * lancer le pipeline dès le premier jour et de compléter la configuration progressivement.
 *
 * Prérequis Jenkins (aucune config globale "SonarQube servers" requise — l'analyse appelle
 * directement l'API Sonar avec un jeton) :
 *   - Plugins : Pipeline, Docker Pipeline, Git, JUnit (déjà installés sur cette instance).
 *   - `mvn`/`java` (via ./mvnw), `node`/`npm`, `sonar-scanner`, `docker`, `kubectl` disponibles
 *     sur l'agent (déjà installés sur cette instance Jenkins).
 *   - Credentials "sonarqube-token" (Secret text) : jeton généré dans SonarQube
 *     (Mon compte > Security > Generate Tokens) — étape à faire une fois par un humain,
 *     jamais par un agent.
 *   - Credentials "docker-hub-creds" (Username/Password) pour le compte Docker Hub
 *     "sirinebb" — déjà présent sur cette instance (réutilisé du pipeline NexLance).
 *   - kubectl utilise le kubeconfig par défaut de l'utilisateur Jenkins
 *     (~/.kube/config = /var/jenkins_home/.kube/config) ; rien à configurer si absent,
 *     l'étape de déploiement est simplement ignorée.
 */

pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        DOCKERHUB_NAMESPACE = 'sirinebb'
        BACKEND_IMAGE       = "${DOCKERHUB_NAMESPACE}/stb-back-office"
        FRONTEND_IMAGE      = "${DOCKERHUB_NAMESPACE}/stb-front-office"
        IMAGE_TAG           = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'local'}"
        BACKEND_DIR         = 'Back_office'
        FRONTEND_DIR        = 'Front_office/template_STB/angular'
        K8S_DIR             = 'k8s'
        // Le client Docker embarqué dans Testcontainers (docker-java, via testcontainers-bom
        // 1.20.4) échoue sa négociation automatique de version d'API contre ce démon Docker
        // (Server API 1.52) : il envoie un premier appel en API 1.32, que ce démon rejette
        // ("Minimum supported API version is 1.44"). Fixer la version cible explicitement
        // court-circuite cette négociation cassée. À utiliser aussi par tout `docker` CLI
        // appelé dans ce pipeline (Build/Push Images), qui respecte la même variable.
        DOCKER_API_VERSION  = '1.52'
        // Le service SonarQube réellement en place n'est pas celui de
        // devops/jenkins/docker-compose.ci.yml (non utilisé) mais un conteneur
        // autonome nommé "jenkins-sonarqube", sur le réseau Docker "jenkins-network"
        // partagé avec ce conteneur Jenkins (résolution par nom de conteneur).
        SONAR_HOST_URL      = 'http://jenkins-sonarqube:9000'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        /*
         * Sonde SonarQube une seule fois, en 5 secondes, plutot que de laisser chaque scanner
         * partir en timeout d'environ 75 s quand le serveur est absent ou encore en cours de
         * demarrage. /api/system/status ne repond {"status":"UP"} que lorsque SonarQube est
         * reellement operationnel (pendant le boot Elasticsearch il repond "STARTING").
         */
        stage('Preflight: SonarQube') {
            steps {
                script {
                    def body = sh(
                        returnStdout: true,
                        script: "curl -s -m 5 --noproxy '*' ${SONAR_HOST_URL}/api/system/status || true"
                    ).trim()
                    env.SONAR_UP = body.contains('"status":"UP"') ? 'true' : 'false'
                    if (env.SONAR_UP == 'true') {
                        echo "SonarQube operationnel sur ${SONAR_HOST_URL} - analyses activees"
                    } else {
                        echo "SonarQube injoignable ou non demarre sur ${SONAR_HOST_URL} " +
                             "(reponse recue: '${body}') - analyses sautees, le pipeline continue"
                    }
                }
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
            when { expression { env.SONAR_UP == 'true' } }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    timeout(time: 10, unit: 'MINUTES') {
                        dir(BACKEND_DIR) {
                            withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                                sh '''
                                    # SonarQube est un service interne au reseau Docker : un proxy
                                    # HTTP herite de l'environnement Jenkins ferait echouer l'appel.
                                    # On le neutralise pour cet hote uniquement.
                                    export NO_PROXY="jenkins-sonarqube,localhost,127.0.0.1"
                                    export no_proxy="$NO_PROXY"
                                    chmod +x ./mvnw
                                    ./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \
                                      -Dsonar.projectKey=stb-bank-back-office \
                                      -Dsonar.projectName="STB Bank - Back Office" \
                                      -Dsonar.host.url=${SONAR_HOST_URL} \
                                      -Dsonar.token=$SONAR_TOKEN \
                                      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                                '''
                            }
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

        stage('Frontend: Unit Tests') {
            steps {
                dir(FRONTEND_DIR) {
                    // Vitest (via le builder @angular/build:unit-test) tourne en headless
                    // sur jsdom : pas besoin de Chrome sur l'agent. --no-watch est requis en
                    // CI (sinon la commande reste bloquée en mode watch).
                    sh 'npx ng test --no-watch'
                }
            }
            post {
                always {
                    junit testResults: "${FRONTEND_DIR}/test-results/junit.xml", allowEmptyResults: true
                }
            }
        }

        stage('Frontend: SonarQube Analysis') {
            when { expression { env.SONAR_UP == 'true' } }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    timeout(time: 10, unit: 'MINUTES') {
                        dir(FRONTEND_DIR) {
                            withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                                sh '''
                                    export NO_PROXY="jenkins-sonarqube,localhost,127.0.0.1"
                                    export no_proxy="$NO_PROXY"
                                    sonar-scanner -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.token=$SONAR_TOKEN
                                '''
                            }
                        }
                    }
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
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-creds',
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
        }

        stage('Deploy: Kubernetes') {
            when { branch 'main' }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh '''
                        kubectl cluster-info --request-timeout=5s
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
        unstable {
            echo "Build ${env.BUILD_NUMBER} instable - une étape optionnelle a échoué (voir logs), le reste du pipeline a continué"
        }
        failure {
            echo "Build ${env.BUILD_NUMBER} en échec - voir les logs des stages ci-dessus"
        }
    }
}
