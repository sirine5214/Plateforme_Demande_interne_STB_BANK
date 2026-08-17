package tn.esprit.stb.back_office.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import tn.esprit.stb.back_office.dto.CreateDemandeRequest;
import tn.esprit.stb.back_office.dto.DemandeDto;
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
import tn.esprit.stb.back_office.repository.HistoriqueStatutRepository;
import tn.esprit.stb.back_office.repository.PieceJointeRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;

/**
 * Tests unitaires du cœur métier : cycle de vie des demandes, cadrage par rôle,
 * pièces jointes et statistiques. Toutes les dépendances sont simulées.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemandeService — gestion des demandes")
class DemandeServiceTest {

    @Mock
    private DemandeRepository demandeRepository;
    @Mock
    private HistoriqueStatutRepository historiqueStatutRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private PieceJointeRepository pieceJointeRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private DemandeService demandeService;

    private Utilisateur demandeur;
    private Utilisateur developpeur;
    private Utilisateur chef;
    private Utilisateur admin;
    private Demande demande;

    @BeforeEach
    void preparerJeuDEssai() {
        demandeur = utilisateur(1, "Sirine", "demandeur@stb.tn", Role.DEMANDEUR);
        developpeur = utilisateur(2, "Wassim", "dev@stb.tn", Role.DEVELOPPEUR);
        chef = utilisateur(3, "Mokhtar", "chef@stb.tn", Role.CHEF_DE_PROJET);
        admin = utilisateur(4, "Admin", "admin@stb.tn", Role.ADMINISTRATEUR);

        demande = demande(10, StatutDemande.NOUVELLE, demandeur, null);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private Utilisateur utilisateur(Integer id, String nom, String email, Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setNom(nom);
        u.setEmail(email);
        u.setRole(role);
        u.setActif(true);
        return u;
    }

    private Demande demande(Integer id, StatutDemande statut, Utilisateur auteur, Utilisateur responsable) {
        Demande d = new Demande();
        d.setId(id);
        d.setNumero("DEM-" + id);
        d.setTitre("Titre " + id);
        d.setDescription("Description");
        d.setPriorite(Priorite.MOYENNE);
        d.setType(TypeDemande.DEVELOPPEMENT);
        d.setStatut(statut);
        d.setDemandeur(auteur);
        d.setResponsable(responsable);
        d.setDateCreation(LocalDateTime.now().minusDays(2));
        return d;
    }

    private CreateDemandeRequest requeteCreation() {
        CreateDemandeRequest requete = new CreateDemandeRequest();
        requete.setTitre("Ajout de l'authentification à deux facteurs");
        requete.setDescription("Sécuriser la connexion");
        requete.setPriorite(Priorite.HAUTE);
        requete.setType(TypeDemande.DEVELOPPEMENT);
        requete.setDateLimite(LocalDate.now().plusDays(10));
        return requete;
    }

    private UpdateDemandeRequest requeteModification(Priorite priorite) {
        UpdateDemandeRequest requete = new UpdateDemandeRequest();
        requete.setTitre("Titre corrigé");
        requete.setDescription("Description corrigée");
        requete.setPriorite(priorite);
        requete.setType(TypeDemande.CORRECTION_BUG);
        requete.setDateLimite(LocalDate.now().plusDays(5));
        return requete;
    }

    /** Simule un save JPA : l'entité passée est renvoyée telle quelle. */
    private void simulerSauvegarde() {
        when(demandeRepository.save(any(Demande.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Liste cadrée par rôle
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("listerPour — cadrage selon le rôle")
    class ListerPour {

        @Test
        @DisplayName("le demandeur ne voit que ses propres demandes")
        void demandeurVoitSesDemandes() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findByDemandeurIdOrderByDateCreationDesc(1)).thenReturn(List.of(demande));

            List<DemandeDto> resultat = demandeService.listerPour(demandeur.getEmail());

            assertThat(resultat).hasSize(1);
            assertThat(resultat.get(0).getDemandeurId()).isEqualTo(1);
            verify(demandeRepository, never()).findAllByOrderByDateCreationDesc();
        }

        @Test
        @DisplayName("le développeur ne voit que les demandes qui lui sont affectées")
        void developpeurVoitSesAffectations() {
            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));
            when(demandeRepository.findByResponsableIdOrderByDateCreationDesc(2)).thenReturn(List.of());

            assertThat(demandeService.listerPour(developpeur.getEmail())).isEmpty();

            verify(demandeRepository).findByResponsableIdOrderByDateCreationDesc(2);
        }

        @Test
        @DisplayName("le chef de projet voit toutes les demandes")
        void chefVoitTout() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findAllByOrderByDateCreationDesc()).thenReturn(List.of(demande));

            assertThat(demandeService.listerPour(chef.getEmail())).hasSize(1);
        }

        @Test
        @DisplayName("un email inconnu est rejeté")
        void utilisateurInconnu() {
            when(utilisateurRepository.findByEmail("fantome@stb.tn")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> demandeService.listerPour("fantome@stb.tn"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("Utilisateur introuvable");
        }
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("creer — dépôt d'une demande")
    class Creer {

        @Test
        @DisplayName("la demande naît en NOUVELLE, sans responsable, et l'historique est tracé")
        void creationInitialiseLeStatut() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(utilisateurRepository.findAll()).thenReturn(List.of(demandeur, chef));
            simulerSauvegarde();

            DemandeDto resultat = demandeService.creer(demandeur.getEmail(), requeteCreation());

            assertThat(resultat.getStatut()).isEqualTo(StatutDemande.NOUVELLE);
            assertThat(resultat.getResponsableId()).isNull();
            assertThat(resultat.getDemandeurId()).isEqualTo(1);
            assertThat(resultat.getNumero()).startsWith("DEM-");

            ArgumentCaptor<HistoriqueStatut> historique = ArgumentCaptor.forClass(HistoriqueStatut.class);
            verify(historiqueStatutRepository).save(historique.capture());
            assertThat(historique.getValue().getAncienStatut()).isNull();
            assertThat(historique.getValue().getNouveauStatut()).isEqualTo(StatutDemande.NOUVELLE);
            assertThat(historique.getValue().getAuteur()).isEqualTo(demandeur);
        }

        @Test
        @DisplayName("seuls les chefs de projet actifs sont notifiés")
        void notifieLesChefsActifsUniquement() {
            Utilisateur chefInactif = utilisateur(5, "Ancien chef", "ancien@stb.tn", Role.CHEF_DE_PROJET);
            chefInactif.setActif(false);

            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(utilisateurRepository.findAll()).thenReturn(List.of(demandeur, developpeur, chef, chefInactif));
            simulerSauvegarde();

            demandeService.creer(demandeur.getEmail(), requeteCreation());

            verify(notificationService).notifier(eq(chef), eq(demandeur), any(Demande.class), contains("a créé une demande"));
            verify(notificationService, never()).notifier(eq(chefInactif), any(), any(), any());
            verify(notificationService, never()).notifier(eq(developpeur), any(), any(), any());
        }
    }

    // ------------------------------------------------------------------
    // Recherche paginée
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("rechercher — recherche multicritère paginée")
    class Rechercher {

        @Test
        @DisplayName("la pagination et le tri décroissant sur la date de création sont transmis")
        void paginationEtTri() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            // Deuxième page de 5 sur 8 éléments : elle en contient 3.
            // PageImpl recalcule le total si le contenu ne colle pas à la pagination.
            when(demandeRepository.findAll(
                    ArgumentMatchers.<Specification<Demande>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(demande, demande, demande), PageRequest.of(1, 5), 8));

            PageResponse<DemandeDto> resultat = demandeService.rechercher(
                    chef.getEmail(), StatutDemande.NOUVELLE, null, null, null, null, null, null, 1, 5);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(demandeRepository).findAll(ArgumentMatchers.<Specification<Demande>>any(), pageable.capture());

            assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
            assertThat(pageable.getValue().getSort().getOrderFor("dateCreation").getDirection())
                    .isEqualTo(Sort.Direction.DESC);

            assertThat(resultat.getContenu()).hasSize(3);
            assertThat(resultat.getTotalElements()).isEqualTo(8);
        }

        @Test
        @DisplayName("un demandeur reste cantonné à ses demandes même en passant un autre responsableId")
        void leDemandeurNePeutPasElargirSonPerimetre() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findAll(
                    ArgumentMatchers.<Specification<Demande>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 8), 0));

            PageResponse<DemandeDto> resultat = demandeService.rechercher(
                    demandeur.getEmail(), null, null, null, 999, null, null, null, 0, 8);

            // Le service impose le cadrage : la requête part malgré le responsableId fourni
            assertThat(resultat.getContenu()).isEmpty();
            assertThat(resultat.getTotalElements()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("consulter — droit de lecture")
    class Consulter {

        @Test
        @DisplayName("l'auteur accède à sa demande")
        void auteurAutorise() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThat(demandeService.consulter(demandeur.getEmail(), 10).getId()).isEqualTo(10);
        }

        @Test
        @DisplayName("un tiers sans lien avec la demande est refusé")
        void tiersRefuse() {
            Utilisateur autreDemandeur = utilisateur(9, "Karim", "karim@stb.tn", Role.DEMANDEUR);
            when(utilisateurRepository.findByEmail(autreDemandeur.getEmail())).thenReturn(Optional.of(autreDemandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> demandeService.consulter(autreDemandeur.getEmail(), 10))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("une demande inexistante est signalée")
        void demandeIntrouvable() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> demandeService.consulter(chef.getEmail(), 404))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("Demande introuvable");
        }
    }

    // ------------------------------------------------------------------
    // Modification
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("modifier — édition du contenu")
    class Modifier {

        @Test
        @DisplayName("l'auteur modifie sa demande tant qu'elle est NOUVELLE")
        void auteurModifieDemandeNouvelle() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            simulerSauvegarde();

            DemandeDto resultat = demandeService.modifier(demandeur.getEmail(), 10, requeteModification(Priorite.MOYENNE));

            assertThat(resultat.getTitre()).isEqualTo("Titre corrigé");
            assertThat(resultat.getType()).isEqualTo(TypeDemande.CORRECTION_BUG);
            // Priorité inchangée : personne n'est notifié
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("l'auteur ne peut plus modifier une demande prise en charge")
        void auteurBloqueApresPriseEnCharge() {
            Demande enCours = demande(11, StatutDemande.EN_COURS, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(11)).thenReturn(Optional.of(enCours));

            assertThatThrownBy(() -> demandeService.modifier(demandeur.getEmail(), 11, requeteModification(Priorite.HAUTE)))
                    .isInstanceOf(AccesRefuseException.class);

            verify(demandeRepository, never()).save(any());
        }

        @Test
        @DisplayName("une demande clôturée est figée, même pour le chef de projet")
        void demandeClotureeFigee() {
            Demande terminee = demande(12, StatutDemande.TERMINEE, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(12)).thenReturn(Optional.of(terminee));

            assertThatThrownBy(() -> demandeService.modifier(chef.getEmail(), 12, requeteModification(Priorite.HAUTE)))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("clôturée");
        }

        @Test
        @DisplayName("un changement de priorité notifie le responsable et le demandeur")
        void changementDePrioriteNotifie() {
            Demande enCours = demande(13, StatutDemande.EN_COURS, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(13)).thenReturn(Optional.of(enCours));
            simulerSauvegarde();

            demandeService.modifier(chef.getEmail(), 13, requeteModification(Priorite.CRITIQUE));

            verify(notificationService).notifier(eq(developpeur), eq(chef), any(Demande.class), contains("priorité"));
            verify(notificationService).notifier(eq(demandeur), eq(chef), any(Demande.class), contains("priorité"));
        }
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("changerStatut — respect du cycle de vie")
    class ChangerStatut {

        @Test
        @DisplayName("le responsable fait avancer sa demande de EN_COURS à EN_VALIDATION")
        void responsableAvanceLaDemande() {
            Demande enCours = demande(20, StatutDemande.EN_COURS, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));
            when(demandeRepository.findById(20)).thenReturn(Optional.of(enCours));
            simulerSauvegarde();

            DemandeDto resultat = demandeService.changerStatut(
                    developpeur.getEmail(), 20, StatutDemande.EN_VALIDATION);

            assertThat(resultat.getStatut()).isEqualTo(StatutDemande.EN_VALIDATION);
            assertThat(resultat.getDateCloture()).isNull();
            verify(historiqueStatutRepository).save(any(HistoriqueStatut.class));
        }

        @Test
        @DisplayName("un tiers ne peut pas toucher au statut")
        void tiersRefuse() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> demandeService.changerStatut(demandeur.getEmail(), 10, StatutDemande.EN_COURS))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("le développeur ne peut ni clôturer ni rejeter")
        void developpeurNePeutPasCloturer() {
            Demande enValidation = demande(21, StatutDemande.EN_VALIDATION, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));
            when(demandeRepository.findById(21)).thenReturn(Optional.of(enValidation));

            assertThatThrownBy(() -> demandeService.changerStatut(developpeur.getEmail(), 21, StatutDemande.TERMINEE))
                    .isInstanceOf(AccesRefuseException.class)
                    .hasMessageContaining("chef de projet");
        }

        @Test
        @DisplayName("les sauts d'étape sont interdits : NOUVELLE → TERMINEE")
        void sautDEtapeInterdit() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> demandeService.changerStatut(chef.getEmail(), 10, StatutDemande.TERMINEE))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("Transition interdite");
        }

        @Test
        @DisplayName("une demande clôturée ne se rouvre pas")
        void demandeClotureeNonReouvrable() {
            Demande terminee = demande(22, StatutDemande.TERMINEE, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(22)).thenReturn(Optional.of(terminee));

            assertThatThrownBy(() -> demandeService.changerStatut(chef.getEmail(), 22, StatutDemande.EN_COURS))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("ne peut plus être modifié");
        }

        @Test
        @DisplayName("passer EN_COURS sans responsable est refusé")
        void enCoursExigeUnResponsable() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> demandeService.changerStatut(chef.getEmail(), 10, StatutDemande.EN_COURS))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("Affectez d'abord un responsable");

            verify(demandeRepository, never()).save(any());
        }

        @Test
        @DisplayName("un statut identique ne déclenche ni sauvegarde ni historique")
        void statutIdentiqueSansEffet() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            DemandeDto resultat = demandeService.changerStatut(chef.getEmail(), 10, StatutDemande.NOUVELLE);

            assertThat(resultat.getStatut()).isEqualTo(StatutDemande.NOUVELLE);
            verify(demandeRepository, never()).save(any());
            verifyNoInteractions(historiqueStatutRepository, notificationService);
        }

        @Test
        @DisplayName("la clôture horodate la demande et informe le demandeur")
        void clotureHorodateEtNotifie() {
            Demande enValidation = demande(23, StatutDemande.EN_VALIDATION, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(23)).thenReturn(Optional.of(enValidation));
            simulerSauvegarde();

            DemandeDto resultat = demandeService.changerStatut(chef.getEmail(), 23, StatutDemande.TERMINEE);

            assertThat(resultat.getStatut()).isEqualTo(StatutDemande.TERMINEE);
            assertThat(resultat.getDateCloture()).isNotNull();
            verify(notificationService).notifier(eq(demandeur), eq(chef), any(Demande.class), contains("terminé"));
        }

        @Test
        @DisplayName("le rejet est notifié au demandeur avec le bon verdict")
        void rejetNotifie() {
            Demande enCours = demande(24, StatutDemande.EN_COURS, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
            when(demandeRepository.findById(24)).thenReturn(Optional.of(enCours));
            simulerSauvegarde();

            demandeService.changerStatut(admin.getEmail(), 24, StatutDemande.REJETEE);

            verify(notificationService).notifier(eq(demandeur), eq(admin), any(Demande.class), contains("rejeté"));
        }
    }

    // ------------------------------------------------------------------
    // Affectation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("affecter — désignation du responsable")
    class Affecter {

        @Test
        @DisplayName("l'affectation d'une demande NOUVELLE la fait passer EN_COURS")
        void affectationBasculeEnCours() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(utilisateurRepository.findById(2)).thenReturn(Optional.of(developpeur));
            simulerSauvegarde();

            DemandeDto resultat = demandeService.affecter(chef.getEmail(), 10, 2);

            assertThat(resultat.getResponsableId()).isEqualTo(2);
            assertThat(resultat.getStatut()).isEqualTo(StatutDemande.EN_COURS);

            ArgumentCaptor<HistoriqueStatut> historique = ArgumentCaptor.forClass(HistoriqueStatut.class);
            verify(historiqueStatutRepository).save(historique.capture());
            assertThat(historique.getValue().getAncienStatut()).isEqualTo(StatutDemande.NOUVELLE);
            assertThat(historique.getValue().getNouveauStatut()).isEqualTo(StatutDemande.EN_COURS);

            verify(notificationService).notifier(eq(developpeur), eq(chef), any(Demande.class), contains("affecté"));
        }

        @Test
        @DisplayName("réaffecter une demande déjà EN_COURS ne rejoue pas l'historique")
        void reaffectationSansHistorique() {
            Demande enCours = demande(30, StatutDemande.EN_COURS, demandeur, developpeur);
            Utilisateur autreDev = utilisateur(6, "Sarra", "sarra@stb.tn", Role.DEVELOPPEUR);

            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(30)).thenReturn(Optional.of(enCours));
            when(utilisateurRepository.findById(6)).thenReturn(Optional.of(autreDev));
            simulerSauvegarde();

            DemandeDto resultat = demandeService.affecter(chef.getEmail(), 30, 6);

            assertThat(resultat.getResponsableId()).isEqualTo(6);
            assertThat(resultat.getStatut()).isEqualTo(StatutDemande.EN_COURS);
            verifyNoInteractions(historiqueStatutRepository);
        }

        @Test
        @DisplayName("un développeur ne peut pas affecter")
        void developpeurNePeutPasAffecter() {
            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));

            assertThatThrownBy(() -> demandeService.affecter(developpeur.getEmail(), 10, 2))
                    .isInstanceOf(AccesRefuseException.class)
                    .hasMessageContaining("Seul un chef de projet");

            verifyNoInteractions(demandeRepository);
        }

        @Test
        @DisplayName("une demande clôturée n'est plus réaffectable — cause du 409 côté interface")
        void demandeClotureeNonReaffectable() {
            Demande rejetee = demande(31, StatutDemande.REJETEE, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(31)).thenReturn(Optional.of(rejetee));

            assertThatThrownBy(() -> demandeService.affecter(chef.getEmail(), 31, 2))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("ne peut plus être réaffectée");
        }

        @Test
        @DisplayName("le responsable doit être un développeur ou un chef de projet")
        void responsableDeMauvaisRole() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(utilisateurRepository.findById(1)).thenReturn(Optional.of(demandeur));

            assertThatThrownBy(() -> demandeService.affecter(chef.getEmail(), 10, 1))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("développeur ou un chef de projet");
        }

        @Test
        @DisplayName("un compte désactivé ne peut pas recevoir de demande")
        void responsableDesactive() {
            Utilisateur devInactif = utilisateur(7, "Parti", "parti@stb.tn", Role.DEVELOPPEUR);
            devInactif.setActif(false);

            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(utilisateurRepository.findById(7)).thenReturn(Optional.of(devInactif));

            assertThatThrownBy(() -> demandeService.affecter(chef.getEmail(), 10, 7))
                    .isInstanceOf(TransitionInvalideException.class)
                    .hasMessageContaining("compte désactivé");
        }

        @Test
        @DisplayName("un responsable inexistant est signalé")
        void responsableIntrouvable() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(utilisateurRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> demandeService.affecter(chef.getEmail(), 10, 999))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("Responsable introuvable");
        }
    }

    // ------------------------------------------------------------------
    // Suppression
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("supprimer — retrait d'une demande")
    class Supprimer {

        @Test
        @DisplayName("l'auteur supprime sa demande tant qu'elle est NOUVELLE")
        void auteurSupprimeDemandeNouvelle() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(historiqueStatutRepository.findByDemandeIdOrderByDateChangementDesc(10)).thenReturn(List.of());

            demandeService.supprimer(demandeur.getEmail(), 10);

            verify(historiqueStatutRepository).deleteAll(List.of());
            verify(demandeRepository).delete(demande);
        }

        @Test
        @DisplayName("l'auteur ne peut plus supprimer une demande prise en charge")
        void auteurBloqueApresPriseEnCharge() {
            Demande enCours = demande(40, StatutDemande.EN_COURS, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(40)).thenReturn(Optional.of(enCours));

            assertThatThrownBy(() -> demandeService.supprimer(demandeur.getEmail(), 40))
                    .isInstanceOf(AccesRefuseException.class);

            // any(Demande.class) et non any() : JpaSpecificationExecutor expose aussi
            // delete(DeleteSpecification), ce qui rendrait l'appel ambigu
            verify(demandeRepository, never()).delete(any(Demande.class));
        }

        @Test
        @DisplayName("le chef de projet supprime une demande à n'importe quel statut")
        void chefSupprimeToujours() {
            Demande terminee = demande(41, StatutDemande.TERMINEE, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(41)).thenReturn(Optional.of(terminee));
            when(historiqueStatutRepository.findByDemandeIdOrderByDateChangementDesc(41)).thenReturn(List.of());

            demandeService.supprimer(chef.getEmail(), 41);

            verify(demandeRepository).delete(terminee);
        }
    }

    // ------------------------------------------------------------------
    // Pièces jointes
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("pièces jointes — dépôt et retrait")
    class PiecesJointes {

        private MockMultipartFile fichier() {
            return new MockMultipartFile("file", "cahier.pdf", "application/pdf", "contenu".getBytes());
        }

        @Test
        @DisplayName("le responsable dépose un livrable")
        void responsableDepose() {
            Demande enCours = demande(50, StatutDemande.EN_COURS, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));
            when(demandeRepository.findById(50)).thenReturn(Optional.of(enCours));
            when(fileStorageService.storeAttachment(any())).thenReturn("/uploads/demandes/abc.pdf");
            when(pieceJointeRepository.save(any(PieceJointe.class))).thenAnswer(i -> i.getArgument(0));

            PieceJointeDto resultat = demandeService.ajouterPieceJointe(developpeur.getEmail(), 50, fichier());

            assertThat(resultat.getNomFichier()).isEqualTo("cahier.pdf");
            assertThat(resultat.getUrl()).isEqualTo("/uploads/demandes/abc.pdf");
            assertThat(resultat.getTailleOctets()).isEqualTo("contenu".getBytes().length);
        }

        @Test
        @DisplayName("un tiers ne peut pas déposer de fichier")
        void tiersRefuse() {
            Demande enCours = demande(51, StatutDemande.EN_COURS, demandeur, developpeur);
            Utilisateur autreDev = utilisateur(6, "Sarra", "sarra@stb.tn", Role.DEVELOPPEUR);

            when(utilisateurRepository.findByEmail(autreDev.getEmail())).thenReturn(Optional.of(autreDev));
            when(demandeRepository.findById(51)).thenReturn(Optional.of(enCours));

            assertThatThrownBy(() -> demandeService.ajouterPieceJointe(autreDev.getEmail(), 51, fichier()))
                    .isInstanceOf(AccesRefuseException.class);

            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("les fichiers d'une demande clôturée sont verrouillés pour le responsable")
        void demandeClotureeVerrouillee() {
            Demande terminee = demande(52, StatutDemande.TERMINEE, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));
            when(demandeRepository.findById(52)).thenReturn(Optional.of(terminee));

            assertThatThrownBy(() -> demandeService.ajouterPieceJointe(developpeur.getEmail(), 52, fichier()))
                    .isInstanceOf(AccesRefuseException.class)
                    .hasMessageContaining("clôturée");
        }

        @Test
        @DisplayName("le chef de projet reste maître des fichiers d'une demande clôturée")
        void chefContourneLeVerrou() {
            Demande terminee = demande(53, StatutDemande.TERMINEE, demandeur, developpeur);
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(53)).thenReturn(Optional.of(terminee));
            when(fileStorageService.storeAttachment(any())).thenReturn("/uploads/demandes/def.pdf");
            when(pieceJointeRepository.save(any(PieceJointe.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(demandeService.ajouterPieceJointe(chef.getEmail(), 53, fichier())).isNotNull();
        }

        @Test
        @DisplayName("la suppression efface le fichier physique puis la ligne en base")
        void suppressionEffaceLeFichier() {
            Demande enCours = demande(54, StatutDemande.EN_COURS, demandeur, developpeur);
            PieceJointe piece = new PieceJointe();
            piece.setId(100);
            piece.setNomFichier("vieux.pdf");
            piece.setCheminFichier("/uploads/demandes/vieux.pdf");
            piece.setDemande(enCours);

            when(utilisateurRepository.findByEmail(developpeur.getEmail())).thenReturn(Optional.of(developpeur));
            when(pieceJointeRepository.findById(100)).thenReturn(Optional.of(piece));

            demandeService.supprimerPieceJointe(developpeur.getEmail(), 100);

            verify(fileStorageService).delete("/uploads/demandes/vieux.pdf");
            verify(pieceJointeRepository).delete(piece);
        }

        @Test
        @DisplayName("la liste des pièces suit le droit de lecture")
        void listeSoumiseAuDroitDeLecture() {
            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(pieceJointeRepository.findByDemandeIdOrderByDateAjoutDesc(10)).thenReturn(List.of());

            assertThat(demandeService.listerPiecesJointes(chef.getEmail(), 10)).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Historique
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("historique — traçabilité des statuts")
    class Historique {

        @Test
        @DisplayName("un tiers ne consulte pas l'historique")
        void tiersRefuse() {
            Utilisateur autreDemandeur = utilisateur(9, "Karim", "karim@stb.tn", Role.DEMANDEUR);
            when(utilisateurRepository.findByEmail(autreDemandeur.getEmail())).thenReturn(Optional.of(autreDemandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> demandeService.historique(autreDemandeur.getEmail(), 10))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("un auteur d'historique supprimé est remplacé par un tiret")
        void auteurAbsentAffichePlaceholder() {
            HistoriqueStatut ligne = new HistoriqueStatut();
            ligne.setId(1);
            ligne.setAncienStatut(StatutDemande.NOUVELLE);
            ligne.setNouveauStatut(StatutDemande.EN_COURS);
            ligne.setAuteur(null);

            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(historiqueStatutRepository.findByDemandeIdOrderByDateChangementDesc(10)).thenReturn(List.of(ligne));

            assertThat(demandeService.historique(chef.getEmail(), 10))
                    .singleElement()
                    .satisfies(dto -> assertThat(dto.getAuteurNom()).isEqualTo("—"));
        }
    }

    // ------------------------------------------------------------------
    // Statistiques
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("statistiques — indicateurs de pilotage")
    class Statistiques {

        @Test
        @DisplayName("les compteurs distinguent en cours, clôturées, en retard et non affectées")
        void compteursCoherents() {
            Demande nouvelleEnRetard = demande(60, StatutDemande.NOUVELLE, demandeur, null);
            nouvelleEnRetard.setDateLimite(LocalDate.now().minusDays(3));

            Demande enCours = demande(61, StatutDemande.EN_COURS, demandeur, developpeur);
            enCours.setDateLimite(LocalDate.now().plusDays(3));

            Demande terminee = demande(62, StatutDemande.TERMINEE, demandeur, developpeur);
            terminee.setDateLimite(LocalDate.now().minusDays(10));
            terminee.setDateCreation(LocalDateTime.now().minusDays(1));
            terminee.setDateCloture(LocalDateTime.now());

            when(utilisateurRepository.findByEmail(chef.getEmail())).thenReturn(Optional.of(chef));
            when(demandeRepository.findAll()).thenReturn(List.of(nouvelleEnRetard, enCours, terminee));

            StatistiquesDto stats = demandeService.statistiques(chef.getEmail());

            assertThat(stats.getTotal()).isEqualTo(3);
            assertThat(stats.getCloturees()).isEqualTo(1);
            assertThat(stats.getOuvertes()).isEqualTo(2);
            // La demande terminée est hors délai mais clôturée : elle ne compte pas comme en retard
            assertThat(stats.getEnRetard()).isEqualTo(1);
            assertThat(stats.getNonAffectees()).isEqualTo(1);
            assertThat(stats.getParStatut()).containsEntry("NOUVELLE", 1L).containsEntry("EN_COURS", 1L);
            assertThat(stats.getParResponsable()).containsEntry("Wassim", 2L);
            assertThat(stats.getEvolutionMensuelle()).hasSize(12);
            assertThat(stats.getTempsMoyenTraitementHeures()).isEqualTo(24.0);
        }

        @Test
        @DisplayName("le demandeur ne voit que le périmètre de ses propres demandes")
        void perimetreDuDemandeur() {
            when(utilisateurRepository.findByEmail(demandeur.getEmail())).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findByDemandeurIdOrderByDateCreationDesc(1)).thenReturn(List.of());

            StatistiquesDto stats = demandeService.statistiques(demandeur.getEmail());

            assertThat(stats.getTotal()).isZero();
            assertThat(stats.getTempsMoyenTraitementHeures()).isZero();
            verify(demandeRepository, never()).findAll();
        }
    }
}
