package tn.esprit.stb.back_office.service.mail;

import java.util.List;

/**
 * Accès en lecture à la boîte partagée qui reçoit les demandes internes.
 *
 * <p>Cette interface isole la logique métier du fournisseur de messagerie. Trois
 * implémentations sont prévues :
 * <ul>
 *   <li>IMAP, pour la messagerie interne de la banque ;</li>
 *   <li>Microsoft Graph, si la STB bascule sur Microsoft 365 ;</li>
 *   <li>{@link FakeMailboxClient}, en mémoire, pour les tests et les démonstrations.</li>
 * </ul>
 *
 * <p>Aucune méthode de suppression n'est exposée, et aucune ne doit l'être : le compte de
 * service utilisé pour la relève doit être en lecture seule sur la boîte.
 */
public interface MailboxClient {

    /**
     * Relève les messages pas encore importés.
     *
     * @param limite nombre maximum de messages ramenés en un passage, pour éviter qu'un
     *               arriéré important ne sature la mémoire au démarrage
     * @return les messages, du plus ancien au plus récent
     */
    List<EmailBrut> recupererNouveauxMessages(int limite);

    /**
     * Signale au serveur qu'un message a été importé, afin qu'il ne soit plus relevé.
     *
     * <p>À n'appeler qu'après la persistance réussie de l'e-mail : en cas d'échec, mieux vaut
     * risquer un doublon — que la contrainte d'unicité sur le « Message-ID » rejettera — que
     * de perdre définitivement une demande.
     */
    void marquerCommeTraite(String messageId);
}
