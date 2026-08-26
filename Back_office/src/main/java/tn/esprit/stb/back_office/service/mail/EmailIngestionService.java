package tn.esprit.stb.back_office.service.mail;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.config.MailIngestionProperties;
import tn.esprit.stb.back_office.entities.EmailEntrant;
import tn.esprit.stb.back_office.entities.PieceJointeEmail;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.StatutEmail;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.repository.EmailEntrantRepository;
import tn.esprit.stb.back_office.repository.PieceJointeEmailRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.service.FileStorageService;
import tn.esprit.stb.back_office.service.NotificationService;

/**
 * Importe les messages de la boîte partagée dans la base, sans jamais créer de demande.
 *
 * <p>L'e-mail reste en {@link StatutEmail#NON_TRAITE} : sa transformation en demande relève
 * de {@link EmailTriageService} et d'une décision humaine. Ce service ne fait que constituer
 * une file d'attente fiable et dédoublonnée.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailIngestionService {

    /** Longueur des colonnes de corps : au-delà, le texte est tronqué plutôt que rejeté. */
    private static final int LONGUEUR_MAX_CORPS = 20_000;
    private static final int LONGUEUR_MAX_SUJET = 500;

    private final MailboxClient mailboxClient;
    private final EmailEntrantRepository emailEntrantRepository;
    private final PieceJointeEmailRepository pieceJointeEmailRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PrequalificationService prequalificationService;
    private final ExtracteurTexteEmail extracteurTexteEmail;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final MailIngestionProperties proprietes;

    /**
     * Relève la boîte et importe ce qui ne l'est pas déjà.
     *
     * @return le nombre de messages effectivement importés
     */
    @Transactional
    public int releverEtImporter() {
        List<EmailBrut> messages =
                mailboxClient.recupererNouveauxMessages(proprietes.getTailleLot());

        int importes = 0;
        for (EmailBrut message : messages) {
            if (emailEntrantRepository.existsByMessageId(message.messageId())) {
                // Deja connu : on marque cote serveur pour ne plus le relever, sans reinserer.
                mailboxClient.marquerCommeTraite(message.messageId());
                continue;
            }
            try {
                importer(message);
                importes++;
            } catch (RuntimeException e) {
                // Un message en echec ne doit pas faire echouer le lot entier : il n'est pas
                // marque cote serveur et sera repropose a la releve suivante.
                log.error("Import de l'e-mail {} impossible : {}", message.messageId(),
                        e.getMessage());
            }
        }

        if (importes > 0) {
            log.info("{} e-mail(s) importe(s) dans la boite de reception", importes);
        }
        return importes;
    }

    private void importer(EmailBrut message) {
        EmailEntrant email = new EmailEntrant();
        email.setMessageId(message.messageId());
        email.setExpediteurEmail(message.expediteurEmail());
        email.setExpediteurNom(message.expediteurNom());
        email.setSujet(tronquer(sujetOuDefaut(message.sujet()), LONGUEUR_MAX_SUJET));
        email.setDateReception(
                message.dateReception() != null ? message.dateReception() : LocalDateTime.now());
        email.setStatut(StatutEmail.NON_TRAITE);

        // Certains clients n'envoient qu'une version HTML : le texte en est alors extrait,
        // sans quoi ces messages arriveraient avec un corps vide. Le HTML lui-meme n'est
        // jamais conserve.
        String texte = message.corpsTexte() != null
                ? message.corpsTexte()
                : extracteurTexteEmail.versTexte(message.corpsHtml());
        email.setCorpsTexte(tronquer(texte, LONGUEUR_MAX_CORPS));

        email.setTypePropose(
                prequalificationService.deduireType(email.getSujet(), email.getCorpsTexte()));
        email.setPrioriteProposee(
                prequalificationService.deduirePriorite(email.getSujet(), email.getCorpsTexte()));

        EmailEntrant enregistre = emailEntrantRepository.save(email);
        enregistrerPiecesJointes(message, enregistre);

        mailboxClient.marquerCommeTraite(message.messageId());
        alerterQualificateurs(enregistre);
    }

    private void enregistrerPiecesJointes(EmailBrut message, EmailEntrant email) {
        for (EmailBrut.PieceJointeBrute fichier : message.piecesJointes()) {
            if (!pieceJointeAcceptee(fichier)) {
                continue;
            }
            PieceJointeEmail pieceJointe = new PieceJointeEmail();
            pieceJointe.setNomFichierOrigine(fichier.nomFichier());
            pieceJointe.setContentType(fichier.contentType());
            pieceJointe.setTailleOctets((long) fichier.contenu().length);
            pieceJointe.setCheminFichier(
                    fileStorageService.storeEmailAttachment(fichier.contenu(), fichier.nomFichier()));
            pieceJointe.setEmailEntrant(email);
            pieceJointeEmailRepository.save(pieceJointe);
        }
    }

    /**
     * Filtre les pièces jointes sur la taille et sur une liste blanche d'extensions.
     *
     * <p>Un fichier refusé est journalisé et abandonné : l'e-mail est tout de même importé,
     * car son texte porte l'essentiel de la demande et le chef de projet peut toujours
     * réclamer le document par retour de courriel.
     */
    private boolean pieceJointeAcceptee(EmailBrut.PieceJointeBrute fichier) {
        if (fichier.nomFichier() == null || fichier.contenu() == null) {
            return false;
        }
        if (fichier.contenu().length > proprietes.getTailleMaxPieceJointe()) {
            log.warn("Piece jointe {} refusee : {} octets depassent la limite de {}",
                    fichier.nomFichier(), fichier.contenu().length,
                    proprietes.getTailleMaxPieceJointe());
            return false;
        }
        String extension = extensionDe(fichier.nomFichier());
        if (!proprietes.getExtensionsAutorisees().contains(extension)) {
            log.warn("Piece jointe {} refusee : extension '{}' hors liste blanche",
                    fichier.nomFichier(), extension);
            return false;
        }
        return true;
    }

    /**
     * Prévient administrateurs et chefs de projet actifs qu'un message attend d'être qualifié.
     *
     * <p>La notification ne porte pas de demande : elle en précède l'existence. Le champ
     * correspondant est donc nul, ce que {@code NotificationService} accepte.
     */
    private void alerterQualificateurs(EmailEntrant email) {
        String message = "Nouvel e-mail de %s : %s".formatted(
                email.getExpediteurNom() != null
                        ? email.getExpediteurNom()
                        : email.getExpediteurEmail(),
                email.getSujet());

        for (Role role : List.of(Role.ADMINISTRATEUR, Role.CHEF_DE_PROJET)) {
            for (Utilisateur destinataire : utilisateurRepository.findByRole(role)) {
                if (Boolean.TRUE.equals(destinataire.getActif())) {
                    notificationService.notifier(destinataire, null, null, message);
                }
            }
        }
    }

    private static String sujetOuDefaut(String sujet) {
        return sujet == null || sujet.isBlank() ? "(sans objet)" : sujet;
    }

    private static String tronquer(String texte, int longueurMax) {
        if (texte == null || texte.length() <= longueurMax) {
            return texte;
        }
        return texte.substring(0, longueurMax);
    }

    private static String extensionDe(String nomFichier) {
        int point = nomFichier.lastIndexOf('.');
        if (point < 0 || point == nomFichier.length() - 1) {
            return "";
        }
        return nomFichier.substring(point + 1).toLowerCase(Locale.ROOT);
    }
}
