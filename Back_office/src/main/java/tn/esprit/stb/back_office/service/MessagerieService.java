package tn.esprit.stb.back_office.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.dto.MessageDto;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.Message;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.TypeNotification;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.AccesRefuseException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.DemandeRepository;
import tn.esprit.stb.back_office.repository.MessageRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.websocket.TempsReelHandler;

/**
 * Fil de discussion rattaché à une demande, entre le demandeur et le responsable.
 * Le chef de projet et l'administrateur y ont accès au titre de la supervision.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagerieService {

    private final MessageRepository messageRepository;
    private final DemandeRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final TempsReelHandler tempsReelHandler;

    @Transactional(readOnly = true)
    public List<MessageDto> lister(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);
        verifierParticipation(utilisateur, demande);

        return messageRepository.findByDemandeIdOrderByDateEnvoiAsc(demandeId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public MessageDto envoyer(String email, Integer demandeId, String contenu) {
        Utilisateur expediteur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);
        verifierParticipation(expediteur, demande);

        Message message = new Message();
        message.setDemande(demande);
        message.setExpediteur(expediteur);
        message.setContenu(contenu.trim());
        message = messageRepository.save(message);

        MessageDto dto = toDto(message);

        // Le message part vers les autres participants, en direct et en notification
        for (Utilisateur destinataire : destinataires(demande, expediteur)) {
            tempsReelHandler.envoyer(destinataire.getEmail(), "MESSAGE", dto);
            notificationService.notifier(
                    destinataire, expediteur, demande,
                    "%s vous a écrit à propos de « %s »".formatted(expediteur.getNom(), demande.getTitre()),
                    TypeNotification.MESSAGE);
        }

        log.info("Message envoyé par {} sur la demande {}", email, demande.getNumero());
        return dto;
    }

    @Transactional
    public void marquerLus(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);
        verifierParticipation(utilisateur, demande);

        List<Message> aMarquer = messageRepository.findByDemandeIdOrderByDateEnvoiAsc(demandeId).stream()
                .filter(m -> !m.getExpediteur().getId().equals(utilisateur.getId()))
                .filter(m -> !Boolean.TRUE.equals(m.getLu()))
                .toList();

        aMarquer.forEach(m -> m.setLu(true));
        messageRepository.saveAll(aMarquer);
    }

    @Transactional(readOnly = true)
    public long compterNonLus(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        return messageRepository.countByDemandeIdAndLuFalseAndExpediteurIdNot(demandeId, utilisateur.getId());
    }

    /** Interlocuteurs à prévenir : l'autre partie du fil, sans doublon ni auto-envoi. */
    private List<Utilisateur> destinataires(Demande demande, Utilisateur expediteur) {
        List<Utilisateur> liste = new ArrayList<>();

        if (!demande.getDemandeur().getId().equals(expediteur.getId())) {
            liste.add(demande.getDemandeur());
        }
        if (demande.getResponsable() != null && !demande.getResponsable().getId().equals(expediteur.getId())) {
            liste.add(demande.getResponsable());
        }

        return liste;
    }

    /** Seuls le demandeur, le responsable et la hiérarchie accèdent au fil. */
    private void verifierParticipation(Utilisateur utilisateur, Demande demande) {
        boolean estDemandeur = demande.getDemandeur().getId().equals(utilisateur.getId());
        boolean estResponsable = demande.getResponsable() != null
                && demande.getResponsable().getId().equals(utilisateur.getId());
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        if (!estDemandeur && !estResponsable && !peutSuperviser) {
            throw new AccesRefuseException("Vous ne participez pas à cette conversation");
        }
    }

    private Demande trouverDemande(Integer id) {
        return demandeRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Demande introuvable"));
    }

    private Utilisateur utilisateurCourant(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable"));
    }

    private MessageDto toDto(Message message) {
        return new MessageDto(
                message.getId(),
                message.getContenu(),
                message.getDateEnvoi(),
                message.getLu(),
                message.getExpediteur().getId(),
                message.getExpediteur().getNom(),
                message.getExpediteur().getPhotoUrl(),
                message.getDemande().getId(),
                message.getDemande().getNumero());
    }
}
