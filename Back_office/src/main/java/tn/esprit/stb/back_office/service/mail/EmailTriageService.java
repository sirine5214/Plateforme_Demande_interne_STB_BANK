package tn.esprit.stb.back_office.service.mail;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.dto.ConversionEmailRequest;
import tn.esprit.stb.back_office.dto.CreateDemandeRequest;
import tn.esprit.stb.back_office.dto.DemandeDto;
import tn.esprit.stb.back_office.dto.EmailEntrantDto;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.dto.PieceJointeEmailDto;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.EmailEntrant;
import tn.esprit.stb.back_office.entities.PieceJointeEmail;
import tn.esprit.stb.back_office.entities.StatutEmail;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.EmailIntrouvableException;
import tn.esprit.stb.back_office.exception.TransitionInvalideException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.DemandeRepository;
import tn.esprit.stb.back_office.repository.EmailEntrantRepository;
import tn.esprit.stb.back_office.repository.PieceJointeEmailRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.service.DemandeService;

/**
 * Qualification des e-mails entrants par un administrateur ou un chef de projet.
 *
 * <p>C'est ici que se joue le passage du monde non authentifié — un message reçu — au monde
 * métier — une demande tracée. Chaque conversion est donc l'acte délibéré d'un agent
 * identifié, dont l'identité est enregistrée dans {@code traitePar}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTriageService {

    private final EmailEntrantRepository emailEntrantRepository;
    private final PieceJointeEmailRepository pieceJointeEmailRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DemandeRepository demandeRepository;
    private final DemandeService demandeService;

    @Transactional(readOnly = true)
    public PageResponse<EmailEntrantDto> lister(StatutEmail statut, int page, int taille) {
        Pageable pagination = PageRequest.of(page, taille);
        Page<EmailEntrant> resultats =
                emailEntrantRepository.findByStatutOrderByDateReceptionDesc(statut, pagination);
        return PageResponse.de(resultats, resultats.getContent().stream().map(this::toDto).toList());
    }

    @Transactional(readOnly = true)
    public EmailEntrantDto consulter(Integer emailId) {
        return toDto(charger(emailId));
    }

    @Transactional(readOnly = true)
    public long compterNonTraites() {
        return emailEntrantRepository.countByStatut(StatutEmail.NON_TRAITE);
    }

    /**
     * Transforme un e-mail qualifié en demande.
     *
     * <p>La création passe par {@link DemandeService} plutôt que par un accès direct au
     * dépôt : la demande hérite ainsi de la numérotation, de l'historique de statut et des
     * notifications aux chefs de projet, exactement comme si elle avait été saisie à l'écran.
     * Une demande née d'un e-mail est une demande ordinaire.
     */
    @Transactional
    public DemandeDto convertir(String emailAgent, Integer emailId, ConversionEmailRequest requete) {
        EmailEntrant email = charger(emailId);
        exigerNonTraite(email);

        Utilisateur agent = utilisateurRepository.findByEmail(emailAgent)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable : " + emailAgent));
        Utilisateur demandeur = resoudreDemandeur(email, requete, agent);

        CreateDemandeRequest creation = new CreateDemandeRequest();
        creation.setTitre(requete.getTitre());
        creation.setDescription(requete.getDescription());
        creation.setPriorite(requete.getPriorite());
        creation.setType(requete.getType());
        creation.setDateLimite(requete.getDateLimite());

        DemandeDto demandeCreee = demandeService.creer(demandeur.getEmail(), creation);

        Demande demande = demandeRepository.findById(demandeCreee.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Demande " + demandeCreee.getId() + " introuvable apres creation"));

        email.setDemande(demande);
        email.setStatut(StatutEmail.CONVERTI);
        email.setTraitePar(agent);
        email.setDateTraitement(LocalDateTime.now());
        emailEntrantRepository.save(email);

        log.info("E-mail {} converti en demande {} par {}", emailId, demandeCreee.getNumero(),
                emailAgent);
        return demandeCreee;
    }

    /**
     * Écarte un e-mail sans créer de demande.
     *
     * <p>Le message n'est jamais supprimé : conserver la trace d'un rejet, avec son motif et
     * son auteur, fait partie de la piste d'audit attendue dans un environnement bancaire.
     */
    @Transactional
    public EmailEntrantDto ignorer(String emailAgent, Integer emailId, String motif) {
        EmailEntrant email = charger(emailId);
        exigerNonTraite(email);

        Utilisateur agent = utilisateurRepository.findByEmail(emailAgent)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable : " + emailAgent));

        email.setStatut(StatutEmail.IGNORE);
        email.setMotifIgnore(motif);
        email.setTraitePar(agent);
        email.setDateTraitement(LocalDateTime.now());

        log.info("E-mail {} ignore par {} : {}", emailId, emailAgent, motif);
        return toDto(emailEntrantRepository.save(email));
    }

    /**
     * Détermine le demandeur de la future demande, par ordre de préférence : le compte
     * explicitement choisi par l'agent, sinon celui dont l'adresse correspond à l'expéditeur,
     * sinon l'agent lui-même.
     *
     * <p>Aucun compte n'est créé à la volée : une adresse d'expédition est déclarative, en
     * faire naître un utilisateur reviendrait à laisser un inconnu peupler l'annuaire.
     */
    private Utilisateur resoudreDemandeur(EmailEntrant email, ConversionEmailRequest requete,
            Utilisateur agent) {

        if (requete.getDemandeurId() != null) {
            return utilisateurRepository.findById(requete.getDemandeurId())
                    .orElseThrow(() -> new UserNotFoundException(
                            "Demandeur introuvable : " + requete.getDemandeurId()));
        }
        return utilisateurRepository.findByEmail(email.getExpediteurEmail()).orElse(agent);
    }

    private EmailEntrant charger(Integer emailId) {
        return emailEntrantRepository.findById(emailId)
                .orElseThrow(() -> new EmailIntrouvableException(emailId));
    }

    private void exigerNonTraite(EmailEntrant email) {
        if (email.getStatut() != StatutEmail.NON_TRAITE) {
            throw new TransitionInvalideException(
                    "Cet e-mail a deja ete traite (" + email.getStatut() + ")");
        }
    }

    private EmailEntrantDto toDto(EmailEntrant email) {
        Optional<Utilisateur> suggere =
                utilisateurRepository.findByEmail(email.getExpediteurEmail());

        List<PieceJointeEmailDto> piecesJointes =
                pieceJointeEmailRepository.findByEmailEntrantId(email.getId()).stream()
                        .map(EmailTriageService::toDto)
                        .toList();

        return new EmailEntrantDto(
                email.getId(),
                email.getExpediteurEmail(),
                email.getExpediteurNom(),
                email.getSujet(),
                email.getCorpsTexte(),
                email.getDateReception(),
                email.getStatut(),
                email.getTypePropose(),
                email.getPrioriteProposee(),
                email.getDemande() != null ? email.getDemande().getNumero() : null,
                email.getDemande() != null ? email.getDemande().getId() : null,
                email.getTraitePar() != null ? email.getTraitePar().getNom() : null,
                email.getDateTraitement(),
                email.getMotifIgnore(),
                suggere.map(Utilisateur::getId).orElse(null),
                suggere.map(Utilisateur::getNom).orElse(null),
                piecesJointes);
    }

    private static PieceJointeEmailDto toDto(PieceJointeEmail piece) {
        return new PieceJointeEmailDto(
                piece.getId(),
                piece.getNomFichierOrigine(),
                piece.getCheminFichier(),
                piece.getContentType(),
                piece.getTailleOctets());
    }
}
