package tn.esprit.stb.back_office;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling active la releve periodique de la boite partagee
// (EmailIngestionScheduler). Sans cette annotation, la tache @Scheduled ne serait
// jamais declenchee, silencieusement.
@SpringBootApplication
@EnableScheduling
public class BackOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackOfficeApplication.class, args);
    }

}
