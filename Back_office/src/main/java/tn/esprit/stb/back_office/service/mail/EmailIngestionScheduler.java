package tn.esprit.stb.back_office.service.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Déclenche la relève de la boîte partagée à intervalle régulier.
 *
 * <p>{@code fixedDelayString} et non {@code fixedRateString} : le délai court à partir de la
 * <em>fin</em> de la relève précédente. Une relève lente — boîte volumineuse, serveur qui
 * répond mal — ne peut donc pas se superposer à la suivante et saturer la connexion IMAP.
 *
 * <p>Désactivable par {@code stb.mail.releve-active=false}, ce qui est le réglage attendu
 * dans les tests et sur les postes de développement où aucune boîte n'est configurée.
 */
@Component
@ConditionalOnProperty(prefix = "stb.mail", name = "releve-active", havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EmailIngestionScheduler {

    private final EmailIngestionService emailIngestionService;

    @Scheduled(fixedDelayString = "${stb.mail.intervalle-ms:120000}",
            initialDelayString = "${stb.mail.delai-initial-ms:30000}")
    public void relever() {
        try {
            emailIngestionService.releverEtImporter();
        } catch (RuntimeException e) {
            // Une exception qui remonte au planificateur annulerait definitivement la tache :
            // un serveur de messagerie momentanement injoignable ne doit pas arreter la
            // releve pour le reste de la vie de l'application.
            log.error("Releve de la boite partagee en echec, nouvelle tentative au prochain "
                    + "passage : {}", e.getMessage());
        }
    }
}
