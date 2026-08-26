package tn.esprit.stb.back_office.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Paramètres de la relève de la boîte partagée qui reçoit les demandes internes.
 *
 * <p>Aucune valeur sensible n'est écrite en dur : {@code username} et {@code password} sont
 * alimentés par des variables d'environnement, au même titre que {@code JWT_SECRET}.
 */
@Component
@ConfigurationProperties(prefix = "stb.mail")
@Getter
@Setter
public class MailIngestionProperties {

    /** Implémentation active : {@code fake} (en mémoire) ou {@code imap}. */
    private String client = "fake";

    private String host = "localhost";

    private int port = 143;

    private String username = "";

    private String password = "";

    /** {@code imap} en clair, {@code imaps} pour une connexion chiffrée. */
    private String protocol = "imap";

    private String dossier = "INBOX";

    /** Relève périodique : désactivable sans toucher au code. */
    private boolean releveActive = true;

    /** Intervalle entre deux relèves, en millisecondes. */
    private long intervalleMs = 120_000L;

    /**
     * Nombre maximum de messages importés par passage.
     *
     * <p>Borne volontaire : à la première mise en service, une boîte contenant des années
     * d'historique serait autrement chargée intégralement en mémoire.
     */
    private int tailleLot = 25;

    /** Taille maximale acceptée pour une pièce jointe, en octets (10 Mo par défaut). */
    private long tailleMaxPieceJointe = 10L * 1024 * 1024;

    /**
     * Liste blanche d'extensions. Tout ce qui n'y figure pas est rejeté : une liste noire
     * serait contournable par la prochaine extension exécutable non anticipée.
     */
    private List<String> extensionsAutorisees = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "png", "jpg", "jpeg", "gif", "zip");
}
