package tn.esprit.stb.back_office.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.dto.NotificationDto;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.Notification;
import tn.esprit.stb.back_office.entities.TypeNotification;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.AccesRefuseException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.NotificationRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.websocket.TempsReelHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final TempsReelHandler tempsReelHandler;

    /**
     * Crée une notification pour un destinataire.
     * Ignoré si le destinataire est absent, ou s'il est lui-même l'auteur de l'action
     * (inutile de se notifier soi-même).
     */
    @Transactional
    public void notifier(Utilisateur destinataire, Utilisateur auteur, Demande demande, String message) {
        notifier(destinataire, auteur, demande, message, TypeNotification.DEMANDE);
    }

    @Transactional
    public void notifier(
            Utilisateur destinataire, Utilisateur auteur, Demande demande, String message, TypeNotification type) {
        if (destinataire == null) {
            return;
        }
        if (auteur != null && destinataire.getId().equals(auteur.getId())) {
            return;
        }

        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setAuteur(auteur);
        notification.setDemande(demande);
        notification.setMessage(message);
        notification.setType(type);

        notification = notificationRepository.save(notification);

        // Poussée immédiate vers les onglets ouverts du destinataire
        tempsReelHandler.envoyer(destinataire.getEmail(), "NOTIFICATION", toDto(notification));

        log.info("Notification envoyée à {} : {}", destinataire.getEmail(), message);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> lister(String email) {
        Utilisateur utilisateur = utilisateurCourant(email);
        return notificationRepository.findByDestinataireIdOrderByDateEnvoiDesc(utilisateur.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long compterNonLues(String email) {
        return notificationRepository.countByDestinataireIdAndLuFalse(utilisateurCourant(email).getId());
    }

    @Transactional
    public NotificationDto marquerCommeLue(String email, Integer notificationId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new UserNotFoundException("Notification introuvable"));

        if (!notification.getDestinataire().getId().equals(utilisateur.getId())) {
            throw new AccesRefuseException("Cette notification ne vous est pas destinée");
        }

        notification.setLu(true);
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public void toutMarquerCommeLu(String email) {
        Utilisateur utilisateur = utilisateurCourant(email);
        List<Notification> nonLues = notificationRepository.findByDestinataireIdAndLuFalseOrderByDateEnvoiDesc(utilisateur.getId());
        nonLues.forEach(n -> n.setLu(true));
        notificationRepository.saveAll(nonLues);
    }

    private Utilisateur utilisateurCourant(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable"));
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getMessage(),
                notification.getDateEnvoi(),
                notification.getLu(),
                notification.getType(),
                notification.getDemande() != null ? notification.getDemande().getId() : null,
                notification.getDemande() != null ? notification.getDemande().getNumero() : null,
                notification.getDemande() != null ? notification.getDemande().getType() : null,
                notification.getAuteur() != null ? notification.getAuteur().getNom() : "Système");
    }
}
