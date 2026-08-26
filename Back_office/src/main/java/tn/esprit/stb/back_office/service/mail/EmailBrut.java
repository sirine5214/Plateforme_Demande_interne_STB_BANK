package tn.esprit.stb.back_office.service.mail;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Message tel qu'il sort du serveur de messagerie, avant tout traitement.
 *
 * <p>Volontairement découplé de l'entité {@code EmailEntrant} : ce type est le contrat entre
 * la plateforme et le monde extérieur, il ne doit dépendre ni de JPA ni du fournisseur de
 * messagerie. C'est ce qui permet de brancher IMAP, Microsoft Graph ou un client de test
 * derrière la même interface.
 *
 * @param messageId       en-tête « Message-ID », clé d'idempotence de la relève
 * @param expediteurEmail adresse de l'expéditeur, <strong>non authentifiée</strong>
 * @param expediteurNom   nom affiché, tout aussi falsifiable que l'adresse
 * @param sujet           objet du message, éventuellement vide
 * @param corpsTexte      corps en texte brut
 * @param corpsHtml       corps HTML <strong>non assaini</strong>, à nettoyer avant stockage
 * @param dateReception   date d'émission annoncée par le serveur
 * @param piecesJointes   fichiers attachés, à valider avant d'être écrits sur disque
 */
public record EmailBrut(
        String messageId,
        String expediteurEmail,
        String expediteurNom,
        String sujet,
        String corpsTexte,
        String corpsHtml,
        LocalDateTime dateReception,
        List<PieceJointeBrute> piecesJointes) {

    public EmailBrut {
        piecesJointes = piecesJointes == null ? List.of() : List.copyOf(piecesJointes);
    }

    /**
     * Fichier attaché, encore en mémoire.
     *
     * <p>Le contenu binaire est exposé tel quel : l'égalité structurelle du record porte donc
     * sur la référence du tableau, ce qui est sans conséquence ici puisque ces objets ne sont
     * ni comparés ni utilisés comme clés.
     *
     * @param nomFichier  nom annoncé par l'expéditeur, à ne jamais utiliser directement comme
     *                    chemin de stockage
     * @param contentType type MIME déclaré, lui aussi déclaratif donc non fiable
     * @param contenu     octets du fichier
     */
    public record PieceJointeBrute(String nomFichier, String contentType, byte[] contenu) {
    }
}
