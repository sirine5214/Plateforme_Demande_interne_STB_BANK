package tn.esprit.stb.back_office;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fournit un vrai Postgres (jetable, démarré via Testcontainers) aux tests qui chargent le
 * contexte Spring complet (ex. {@link BackOfficeApplicationTests}). @ServiceConnection fait
 * en sorte que Spring Boot reconfigure automatiquement le datasource vers ce conteneur -
 * spring.datasource.* de application.properties (Postgres local sur localhost:5434, non
 * disponible depuis l'agent Jenkins) est alors ignoré pour ces tests.
 *
 * Nécessite un démon Docker accessible (socket monté) là où tourne le build - déjà le cas en
 * local et sur l'agent Jenkins de ce projet.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

}
