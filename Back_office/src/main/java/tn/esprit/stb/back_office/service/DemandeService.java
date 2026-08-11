package tn.esprit.stb.back_office.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.dto.CreateDemandeRequest;
import tn.esprit.stb.back_office.dto.DemandeDto;
import tn.esprit.stb.back_office.dto.HistoriqueStatutDto;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.dto.PieceJointeDto;
import tn.esprit.stb.back_office.dto.StatistiquesDto;
import tn.esprit.stb.back_office.dto.UpdateDemandeRequest;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.HistoriqueStatut;
import tn.esprit.stb.back_office.entities.PieceJointe;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.AccesRefuseException;
import tn.esprit.stb.back_office.exception.TransitionInvalideException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.DemandeRepository;
import tn.esprit.stb.back_office.repository.DemandeSpecifications;
import tn.esprit.stb.back_office.repository.HistoriqueStatutRepository;
import tn.esprit.stb.back_office.repository.PieceJointeRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemandeService {

    private final DemandeRepository demandeRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    /** Liste des demandes visibles par l'utilisateur courant, selon son rôle. */
    @Transactional(readOnly = true)
    public List<DemandeDto> listerPour(String email) {
        Utilisateur utilisateur = utilisateurCourant(email);

        List<Demande> demandes = switch (utilisateur.getRole()) {
            case DEMANDEUR -> demandeRepository.findByDemandeurIdOrderByDateCreationDesc(utilisateur.getId());
            case DEVELOPPEUR -> demandeRepository.findByResponsableIdOrderByDateCreationDesc(utilisateur.getId());
            case CHEF_DE_PROJET, ADMINISTRATEUR -> demandeRepository.findAllByOrderByDateCreationDesc();
        };

        return demandes.stream().map(this::toDto).toList();
    }

    @Transactional
    public DemandeDto creer(String email, CreateDemandeRequest request) {
        Utilisateur demandeur = utilisateurCourant(email);

        Demande demande = new Demande();
        demande.setNumero(genererNumero());
        demande.setTitre(request.getTitre());
        demande.setDescription(request.getDescription());
        demande.setPriorite(request.getPriorite());
        demande.setType(request.getType());
        demande.setDateLimite(request.getDateLimite());
        demande.setStatut(StatutDemande.NOUVELLE);
        demande.setDemandeur(demandeur);

        demande = demandeRepository.save(demande);
        enregistrerHistorique(demande, null, StatutDemande.NOUVELLE, demandeur);

        // Notification de création : les chefs de projet sont alertés qu'une demande est à affecter
        String message = "%s a créé une demande « %s » (%s)"
                .formatted(demandeur.getNom(), demande.getTitre(), libelle(demande.getType()));
        for (Utilisateur chef : utilisateurRepository.findAll()) {
            if (chef.getRole() == Role.CHEF_DE_PROJET && Boolean.TRUE.equals(chef.getActif())) {
                notificationService.notifier(chef, demandeur, demande, message);
            }
        }

        log.info("Demande {} créée par {}", demande.getNumero(), email);
        return toDto(demande);
    }

    /**
     * Recherche multicritère paginée (BF 2.6).
     * Le périmètre est imposé côté serveur selon le rôle : un demandeur ne voit que ses demandes,
     * un développeur uniquement celles qui lui sont affectées.
     */
    @Transactional(readOnly = true)
    public PageResponse<DemandeDto> rechercher(
            String email,
            StatutDemande statut,
            Priorite priorite,
            TypeDemande type,
            Integer responsableId,
            String motCle,
            LocalDate dateDebut,
            LocalDate dateFin,
            int page,
            int taille) {

        Utilisateur utilisateur = utilisateurCourant(email);

        Integer filtreDemandeur = null;
        Integer filtreResponsable = responsableId;

        switch (utilisateur.getRole()) {
            case DEMANDEUR -> filtreDemandeur = utilisateur.getId();
            case DEVELOPPEUR -> filtreResponsable = utilisateur.getId();
            case CHEF_DE_PROJET, ADMINISTRATEUR -> {
                // périmètre complet, les filtres restent ceux demandés
            }
        }

        Pageable pageable = PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateCreation"));

        Page<Demande> resultats = demandeRepository.findAll(
                DemandeSpecifications.avecCriteres(
                        statut,
                        priorite,
                        type,
                        filtreResponsable,
                        filtreDemandeur,
                        dateDebut != null ? dateDebut.atStartOfDay() : null,
                        dateFin != null ? dateFin.atTime(LocalTime.MAX) : null,
                        motCle),
                pageable);

        return PageResponse.de(resultats, resultats.getContent().stream().map(this::toDto).toList());
    }

    /** Consultation d'une demande précise — permet d'ouvrir une demande depuis une notification. */
    @Transactional(readOnly = true)
    public DemandeDto consulter(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);
        verifierAccesLecture(utilisateur, demande);
        return toDto(demande);
    }

    /** Modification du contenu d'une demande (BF 2.2.3). */
    @Transactional
    public DemandeDto modifier(String email, Integer demandeId, UpdateDemandeRequest request) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);

        boolean estAuteur = demande.getDemandeur().getId().equals(utilisateur.getId());
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        // Le demandeur ne peut modifier sa demande que tant qu'elle n'est pas prise en charge
        if (!peutSuperviser && !(estAuteur && demande.getStatut() == StatutDemande.NOUVELLE)) {
            throw new AccesRefuseException("Vous n'êtes pas autorisé à modifier cette demande");
        }

        // Une demande clôturée est figée, y compris pour le chef de projet
        if (demande.getStatut().estFinal()) {
            throw new TransitionInvalideException("Une demande clôturée ne peut plus être modifiée");
        }

        Priorite anciennePriorite = demande.getPriorite();

        demande.setTitre(request.getTitre());
        demande.setDescription(request.getDescription());
        demande.setPriorite(request.getPriorite());
        demande.setType(request.getType());
        demande.setDateLimite(request.getDateLimite());

        demande = demandeRepository.save(demande);

        // Notification de changement de priorité
        if (anciennePriorite != request.getPriorite()) {
            String message = "%s a changé la priorité de « %s » (%s) : %s → %s".formatted(
                    utilisateur.getNom(),
                    demande.getTitre(),
                    libelle(demande.getType()),
                    libelle(anciennePriorite),
                    libelle(request.getPriorite()));
            notificationService.notifier(demande.getResponsable(), utilisateur, demande, message);
            notificationService.notifier(demande.getDemandeur(), utilisateur, demande, message);
        }

        log.info("Demande {} modifiée par {}", demande.getNumero(), email);
        return toDto(demande);
    }

    // ---------- Pièces jointes (BF 2.2.6) ----------

    @Transactional
    public PieceJointeDto ajouterPieceJointe(String email, Integer demandeId, MultipartFile fichier) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);
        verifierGestionFichiers(utilisateur, demande);

        PieceJointe piece = new PieceJointe();
        piece.setDemande(demande);
        piece.setNomFichier(fichier.getOriginalFilename());
        piece.setCheminFichier(fileStorageService.storeAttachment(fichier));
        piece.setTailleOctets(fichier.getSize());

        piece = pieceJointeRepository.save(piece);
        log.info("Pièce jointe « {} » ajoutée à la demande {} par {}", piece.getNomFichier(), demande.getNumero(), email);

        return toPieceJointeDto(piece);
    }

    @Transactional(readOnly = true)
    public List<PieceJointeDto> listerPiecesJointes(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);
        verifierAccesLecture(utilisateur, demande);

        return pieceJointeRepository.findByDemandeIdOrderByDateAjoutDesc(demandeId).stream()
                .map(this::toPieceJointeDto)
                .toList();
    }

    @Transactional
    public void supprimerPieceJointe(String email, Integer pieceId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        PieceJointe piece = pieceJointeRepository.findById(pieceId)
                .orElseThrow(() -> new UserNotFoundException("Pièce jointe introuvable"));

        verifierGestionFichiers(utilisateur, piece.getDemande());

        fileStorageService.delete(piece.getCheminFichier());
        pieceJointeRepository.delete(piece);
    }

    /**
     * Le responsable dépose ses livrables, le chef de projet supervise, et le demandeur
     * complète son dossier tant que sa demande n'est pas prise en charge.
     * Une demande clôturée n'est plus modifiable, sauf par le chef de projet.
     */
    private void verifierGestionFichiers(Utilisateur utilisateur, Demande demande) {
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        if (peutSuperviser) {
            return;
        }

        if (demande.getStatut() == StatutDemande.TERMINEE || demande.getStatut() == StatutDemande.REJETEE) {
            throw new AccesRefuseException("Les fichiers d'une demande clôturée ne sont plus modifiables");
        }

        boolean estResponsable = demande.getResponsable() != null
                && demande.getResponsable().getId().equals(utilisateur.getId());
        boolean estAuteurNonTraitee = demande.getDemandeur().getId().equals(utilisateur.getId())
                && demande.getStatut() == StatutDemande.NOUVELLE;

        if (!estResponsable && !estAuteurNonTraitee) {
            throw new AccesRefuseException("Vous n'êtes pas autorisé à gérer les fichiers de cette demande");
        }
    }

    private PieceJointeDto toPieceJointeDto(PieceJointe piece) {
        return new PieceJointeDto(
                piece.getId(), piece.getNomFichier(), piece.getCheminFichier(), piece.getTailleOctets(), piece.getDateAjout());
    }

    /** Un utilisateur accède à une demande s'il en est l'auteur, le responsable, ou s'il supervise. */
    private void verifierAccesLecture(Utilisateur utilisateur, Demande demande) {
        boolean estAuteur = demande.getDemandeur().getId().equals(utilisateur.getId());
        boolean estResponsable = demande.getResponsable() != null
                && demande.getResponsable().getId().equals(utilisateur.getId());
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        if (!estAuteur && !estResponsable && !peutSuperviser) {
            throw new AccesRefuseException("Vous n'êtes pas autorisé à accéder à cette demande");
        }
    }

    /** Historique des changements de statut d'une demande (BF 2.3.2). */
    @Transactional(readOnly = true)
    public List<HistoriqueStatutDto> historique(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);

        boolean estAuteur = demande.getDemandeur().getId().equals(utilisateur.getId());
        boolean estResponsable = demande.getResponsable() != null
                && demande.getResponsable().getId().equals(utilisateur.getId());
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        if (!estAuteur && !estResponsable && !peutSuperviser) {
            throw new AccesRefuseException("Vous n'êtes pas autorisé à consulter cette demande");
        }

        return historiqueStatutRepository.findByDemandeIdOrderByDateChangementDesc(demandeId).stream()
                .map(h -> new HistoriqueStatutDto(
                        h.getId(),
                        h.getAncienStatut(),
                        h.getNouveauStatut(),
                        h.getDateChangement(),
                        h.getAuteur() != null ? h.getAuteur().getNom() : "—"))
                .toList();
    }

    @Transactional
    public DemandeDto changerStatut(String email, Integer demandeId, StatutDemande nouveauStatut) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);

        boolean estResponsable = demande.getResponsable() != null
                && demande.getResponsable().getId().equals(utilisateur.getId());
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        if (!estResponsable && !peutSuperviser) {
            throw new AccesRefuseException("Vous n'êtes pas autorisé à modifier le statut de cette demande");
        }

        // Seuls le chef de projet et l'administrateur peuvent clôturer ou rejeter
        if (nouveauStatut.estFinal() && !peutSuperviser) {
            throw new AccesRefuseException("Seul un chef de projet peut clôturer ou rejeter une demande");
        }

        StatutDemande ancienStatut = demande.getStatut();

        if (ancienStatut == nouveauStatut) {
            return toDto(demande);
        }

        // Une demande clôturée est définitive : on ne la rouvre pas
        if (ancienStatut.estFinal()) {
            throw new TransitionInvalideException(
                    "La demande est %s : son statut ne peut plus être modifié".formatted(libelle(ancienStatut)));
        }

        // Le cycle de vie du cahier des charges interdit les sauts d'étape
        if (!ancienStatut.peutAllerVers(nouveauStatut)) {
            throw new TransitionInvalideException("Transition interdite : %s → %s"
                    .formatted(libelle(ancienStatut), libelle(nouveauStatut)));
        }

        // Une demande ne peut être prise en charge sans responsable désigné
        if (nouveauStatut == StatutDemande.EN_COURS && demande.getResponsable() == null) {
            throw new TransitionInvalideException("Affectez d'abord un responsable avant de passer la demande en cours");
        }

        demande.setStatut(nouveauStatut);
        demande.setDateCloture(nouveauStatut.estFinal() ? LocalDateTime.now() : null);

        demande = demandeRepository.save(demande);
        enregistrerHistorique(demande, ancienStatut, nouveauStatut, utilisateur);

        // Notification de clôture : le demandeur est informé du résultat
        if (nouveauStatut == StatutDemande.TERMINEE || nouveauStatut == StatutDemande.REJETEE) {
            String verdict = nouveauStatut == StatutDemande.TERMINEE ? "terminé" : "rejeté";
            notificationService.notifier(
                    demande.getDemandeur(), utilisateur, demande,
                    "%s a %s votre demande « %s » (%s)"
                            .formatted(utilisateur.getNom(), verdict, demande.getTitre(), libelle(demande.getType())));
        } else {
            // Le responsable suit l'évolution des demandes dont il a la charge
            notificationService.notifier(
                    demande.getResponsable(), utilisateur, demande,
                    "%s a fait passer « %s » (%s) au statut %s"
                            .formatted(utilisateur.getNom(), demande.getTitre(), libelle(demande.getType()), libelle(nouveauStatut)));
        }

        log.info("Demande {} : {} -> {} par {}", demande.getNumero(), ancienStatut, nouveauStatut, email);
        return toDto(demande);
    }

    @Transactional
    public DemandeDto affecter(String email, Integer demandeId, Integer responsableId) {
        Utilisateur utilisateur = utilisateurCourant(email);

        if (utilisateur.getRole() != Role.CHEF_DE_PROJET && utilisateur.getRole() != Role.ADMINISTRATEUR) {
            throw new AccesRefuseException("Seul un chef de projet peut affecter une demande");
        }

        Demande demande = trouverDemande(demandeId);

        if (demande.getStatut().estFinal()) {
            throw new TransitionInvalideException("Une demande clôturée ne peut plus être réaffectée");
        }

        Utilisateur responsable = utilisateurRepository.findById(responsableId)
                .orElseThrow(() -> new UserNotFoundException("Responsable introuvable"));

        // Seuls un développeur ou un chef de projet peuvent traiter une demande
        if (responsable.getRole() != Role.DEVELOPPEUR && responsable.getRole() != Role.CHEF_DE_PROJET) {
            throw new TransitionInvalideException("Le responsable doit être un développeur ou un chef de projet");
        }
        if (!Boolean.TRUE.equals(responsable.getActif())) {
            throw new TransitionInvalideException("Impossible d'affecter une demande à un compte désactivé");
        }

        demande.setResponsable(responsable);

        // Une demande affectée passe automatiquement en cours de traitement
        if (demande.getStatut() == StatutDemande.NOUVELLE) {
            StatutDemande ancienStatut = demande.getStatut();
            demande.setStatut(StatutDemande.EN_COURS);
            demande = demandeRepository.save(demande);
            enregistrerHistorique(demande, ancienStatut, StatutDemande.EN_COURS, utilisateur);
        } else {
            demande = demandeRepository.save(demande);
        }

        // Notification d'affectation
        notificationService.notifier(
                responsable, utilisateur, demande,
                "%s vous a affecté la demande « %s » (%s)"
                        .formatted(utilisateur.getNom(), demande.getTitre(), libelle(demande.getType())));

        log.info("Demande {} affectée à {} par {}", demande.getNumero(), responsable.getEmail(), email);
        return toDto(demande);
    }

    @Transactional
    public void supprimer(String email, Integer demandeId) {
        Utilisateur utilisateur = utilisateurCourant(email);
        Demande demande = trouverDemande(demandeId);

        boolean estAuteur = demande.getDemandeur().getId().equals(utilisateur.getId());
        boolean peutSuperviser = utilisateur.getRole() == Role.CHEF_DE_PROJET
                || utilisateur.getRole() == Role.ADMINISTRATEUR;

        // Un demandeur ne peut supprimer que ses propres demandes non encore traitées
        if (!peutSuperviser && !(estAuteur && demande.getStatut() == StatutDemande.NOUVELLE)) {
            throw new AccesRefuseException("Vous n'êtes pas autorisé à supprimer cette demande");
        }

        historiqueStatutRepository.deleteAll(historiqueStatutRepository.findByDemandeIdOrderByDateChangementDesc(demandeId));
        demandeRepository.delete(demande);
    }

    @Transactional(readOnly = true)
    /**
     * Statistiques cadrées sur le périmètre de l'utilisateur, comme la liste des demandes :
     * le demandeur voit les siennes, le développeur celles qui lui sont affectées,
     * le chef de projet et l'administrateur voient l'ensemble.
     */
    public StatistiquesDto statistiques(String email) {
        Utilisateur utilisateur = utilisateurCourant(email);

        List<Demande> demandes = switch (utilisateur.getRole()) {
            case DEMANDEUR -> demandeRepository.findByDemandeurIdOrderByDateCreationDesc(utilisateur.getId());
            case DEVELOPPEUR -> demandeRepository.findByResponsableIdOrderByDateCreationDesc(utilisateur.getId());
            case CHEF_DE_PROJET, ADMINISTRATEUR -> demandeRepository.findAll();
        };

        long total = demandes.size();
        long cloturees = demandes.stream().filter(d -> d.getStatut().estFinal()).count();

        // Une demande est en retard si sa date limite est passée et qu'elle n'est pas clôturée
        LocalDate aujourdhui = LocalDate.now();
        long enRetard = demandes.stream()
                .filter(d -> !d.getStatut().estFinal())
                .filter(d -> d.getDateLimite() != null && d.getDateLimite().isBefore(aujourdhui))
                .count();

        long nonAffectees = demandes.stream()
                .filter(d -> !d.getStatut().estFinal())
                .filter(d -> d.getResponsable() == null)
                .count();

        Map<String, Long> parStatut = demandes.stream()
                .collect(Collectors.groupingBy(d -> d.getStatut().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> parPriorite = demandes.stream()
                .collect(Collectors.groupingBy(d -> d.getPriorite().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> parType = demandes.stream()
                .collect(Collectors.groupingBy(d -> d.getType().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> parResponsable = demandes.stream()
                .filter(d -> d.getResponsable() != null)
                .collect(Collectors.groupingBy(d -> d.getResponsable().getNom(), LinkedHashMap::new, Collectors.counting()));

        return new StatistiquesDto(
                total,
                total - cloturees,
                cloturees,
                parStatut,
                parPriorite,
                parResponsable,
                parType,
                enRetard,
                nonAffectees,
                evolutionMensuelle(demandes),
                tempsMoyenTraitementHeures(demandes));
    }

    /** Nombre de demandes créées sur chacun des 12 derniers mois, mois vides inclus. */
    private Map<String, Long> evolutionMensuelle(List<Demande> demandes) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> evolution = new LinkedHashMap<>();

        YearMonth debut = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            evolution.put(debut.plusMonths(i).format(format), 0L);
        }

        for (Demande demande : demandes) {
            String cle = YearMonth.from(demande.getDateCreation()).format(format);
            evolution.computeIfPresent(cle, (k, valeur) -> valeur + 1);
        }

        return evolution;
    }

    /** Moyenne des durées création → clôture, en heures. */
    private double tempsMoyenTraitementHeures(List<Demande> demandes) {
        return demandes.stream()
                .filter(d -> d.getDateCloture() != null)
                .mapToLong(d -> Duration.between(d.getDateCreation(), d.getDateCloture()).toMinutes())
                .average()
                .stream()
                .map(minutes -> Math.round(minutes / 60 * 10) / 10.0)
                .findFirst()
                .orElse(0.0);
    }

    private void enregistrerHistorique(Demande demande, StatutDemande ancien, StatutDemande nouveau, Utilisateur auteur) {
        HistoriqueStatut historique = new HistoriqueStatut();
        historique.setDemande(demande);
        historique.setAncienStatut(ancien);
        historique.setNouveauStatut(nouveau);
        historique.setAuteur(auteur);
        historiqueStatutRepository.save(historique);
    }

    private String genererNumero() {
        return "DEM-" + System.currentTimeMillis();
    }

    /** Transforme une valeur d'énumération en libellé lisible : CORRECTION_BUG → « Correction bug ». */
    private String libelle(Enum<?> valeur) {
        if (valeur == null) {
            return "";
        }
        String texte = valeur.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    private Demande trouverDemande(Integer id) {
        return demandeRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Demande introuvable"));
    }

    private Utilisateur utilisateurCourant(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable"));
    }

    private DemandeDto toDto(Demande demande) {
        return new DemandeDto(
                demande.getId(),
                demande.getNumero(),
                demande.getTitre(),
                demande.getDescription(),
                demande.getPriorite(),
                demande.getStatut(),
                demande.getType(),
                demande.getDateCreation(),
                demande.getDateLimite(),
                demande.getDateCloture(),
                demande.getDemandeur().getId(),
                demande.getDemandeur().getNom(),
                demande.getResponsable() != null ? demande.getResponsable().getId() : null,
                demande.getResponsable() != null ? demande.getResponsable().getNom() : null);
    }
}
