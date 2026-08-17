package tn.esprit.stb.back_office.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.security.crypto.password.PasswordEncoder;

import tn.esprit.stb.back_office.dto.AdminUpdateUserRequest;
import tn.esprit.stb.back_office.dto.CreateUserRequest;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.dto.UpdateProfileRequest;
import tn.esprit.stb.back_office.dto.UtilisateurDto;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.EmailAlreadyUsedException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;

/** Tests unitaires de la gestion des comptes utilisateurs (profil et administration). */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — gestion des utilisateurs")
class UserServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Utilisateur demandeur;
    private Utilisateur developpeur;
    private Utilisateur chef;

    @BeforeEach
    void preparerJeuDEssai() {
        demandeur = utilisateur(1, "Sirine", "demandeur@stb.tn", Role.DEMANDEUR, true);
        developpeur = utilisateur(2, "Wassim", "dev@stb.tn", Role.DEVELOPPEUR, true);
        chef = utilisateur(3, "Mokhtar", "chef@stb.tn", Role.CHEF_DE_PROJET, true);
    }

    private Utilisateur utilisateur(Integer id, String nom, String email, Role role, boolean actif) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setNom(nom);
        u.setEmail(email);
        u.setMotDePasse("hash-existant");
        u.setRole(role);
        u.setActif(actif);
        return u;
    }

    private void simulerSauvegarde() {
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("listAffectables — candidats à une affectation")
    class ListAffectables {

        @Test
        @DisplayName("ne retient que les développeurs et chefs de projet actifs")
        void filtreParRoleEtActivite() {
            Utilisateur devInactif = utilisateur(4, "Parti", "parti@stb.tn", Role.DEVELOPPEUR, false);
            Utilisateur admin = utilisateur(5, "Admin", "admin@stb.tn", Role.ADMINISTRATEUR, true);

            when(utilisateurRepository.findAll())
                    .thenReturn(List.of(demandeur, developpeur, chef, devInactif, admin));

            List<UtilisateurDto> resultat = userService.listAffectables();

            assertThat(resultat).extracting(UtilisateurDto::getNom).containsExactly("Wassim", "Mokhtar");
        }

        @Test
        @DisplayName("renvoie une liste vide quand aucun candidat n'existe")
        void aucunCandidat() {
            when(utilisateurRepository.findAll()).thenReturn(List.of(demandeur));

            assertThat(userService.listAffectables()).isEmpty();
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("updateProfile — modification de son propre profil")
    class UpdateProfile {

        @Test
        @DisplayName("le nom est mis à jour et le mot de passe conservé quand il n'est pas fourni")
        void motDePasseInchangeSiAbsent() {
            UpdateProfileRequest requete = new UpdateProfileRequest();
            requete.setNom("Sirine Ben Cheikh");
            requete.setNouveauMotDePasse("   ");

            when(utilisateurRepository.findByEmail("demandeur@stb.tn")).thenReturn(Optional.of(demandeur));
            simulerSauvegarde();

            UtilisateurDto resultat = userService.updateProfile("demandeur@stb.tn", requete);

            assertThat(resultat.getNom()).isEqualTo("Sirine Ben Cheikh");
            assertThat(demandeur.getMotDePasse()).isEqualTo("hash-existant");
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("un nouveau mot de passe est encodé avant enregistrement")
        void nouveauMotDePasseEncode() {
            UpdateProfileRequest requete = new UpdateProfileRequest();
            requete.setNom("Sirine");
            requete.setNouveauMotDePasse("Secret123");

            when(utilisateurRepository.findByEmail("demandeur@stb.tn")).thenReturn(Optional.of(demandeur));
            when(passwordEncoder.encode("Secret123")).thenReturn("hash-nouveau");
            simulerSauvegarde();

            userService.updateProfile("demandeur@stb.tn", requete);

            assertThat(demandeur.getMotDePasse()).isEqualTo("hash-nouveau");
        }

        @Test
        @DisplayName("un compte inconnu est rejeté")
        void compteInconnu() {
            when(utilisateurRepository.findByEmail("fantome@stb.tn")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile("fantome@stb.tn", new UpdateProfileRequest()))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("create — création par l'administrateur")
    class Create {

        private CreateUserRequest requete() {
            CreateUserRequest requete = new CreateUserRequest();
            requete.setNom("Nouvelle recrue");
            requete.setEmail("recrue@stb.tn");
            requete.setMotDePasse("Password123");
            requete.setRole(Role.DEVELOPPEUR);
            return requete;
        }

        @Test
        @DisplayName("le compte est créé actif, avec un mot de passe encodé")
        void creationActiveEtEncodee() {
            when(utilisateurRepository.existsByEmail("recrue@stb.tn")).thenReturn(false);
            when(passwordEncoder.encode("Password123")).thenReturn("hash");
            simulerSauvegarde();

            UtilisateurDto resultat = userService.create(requete());

            assertThat(resultat.getActif()).isTrue();
            assertThat(resultat.getRole()).isEqualTo(Role.DEVELOPPEUR);

            ArgumentCaptor<Utilisateur> enregistre = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(enregistre.capture());
            assertThat(enregistre.getValue().getMotDePasse()).isEqualTo("hash");
        }

        @Test
        @DisplayName("un email déjà utilisé bloque la création")
        void emailDejaUtilise() {
            when(utilisateurRepository.existsByEmail("recrue@stb.tn")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(requete()))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verify(utilisateurRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("update — modification par l'administrateur")
    class Update {

        private AdminUpdateUserRequest requete(String email, String motDePasse) {
            AdminUpdateUserRequest requete = new AdminUpdateUserRequest();
            requete.setNom("Wassim Trabelsi");
            requete.setEmail(email);
            requete.setRole(Role.CHEF_DE_PROJET);
            requete.setActif(true);
            requete.setNouveauMotDePasse(motDePasse);
            return requete;
        }

        @Test
        @DisplayName("conserver le même email ne déclenche pas le contrôle d'unicité")
        void emailInchangeSansControle() {
            when(utilisateurRepository.findById(2)).thenReturn(Optional.of(developpeur));
            simulerSauvegarde();

            UtilisateurDto resultat = userService.update(2, requete("dev@stb.tn", null));

            assertThat(resultat.getRole()).isEqualTo(Role.CHEF_DE_PROJET);
            verify(utilisateurRepository, never()).existsByEmail(any());
        }

        @Test
        @DisplayName("changer pour un email déjà pris est refusé")
        void nouvelEmailDejaPris() {
            when(utilisateurRepository.findById(2)).thenReturn(Optional.of(developpeur));
            when(utilisateurRepository.existsByEmail("chef@stb.tn")).thenReturn(true);

            assertThatThrownBy(() -> userService.update(2, requete("chef@stb.tn", null)))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verify(utilisateurRepository, never()).save(any());
        }

        @Test
        @DisplayName("un mot de passe fourni est encodé")
        void motDePasseReinitialise() {
            when(utilisateurRepository.findById(2)).thenReturn(Optional.of(developpeur));
            when(passwordEncoder.encode("Nouveau123")).thenReturn("hash-admin");
            simulerSauvegarde();

            userService.update(2, requete("dev@stb.tn", "Nouveau123"));

            assertThat(developpeur.getMotDePasse()).isEqualTo("hash-admin");
        }

        @Test
        @DisplayName("un identifiant inconnu est signalé")
        void identifiantInconnu() {
            when(utilisateurRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(99, requete("x@stb.tn", null)))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("toggleStatus / delete / updateRole")
    class ActionsAdministratives {

        @Test
        @DisplayName("toggleStatus bascule un compte actif en inactif")
        void desactivation() {
            when(utilisateurRepository.findById(2)).thenReturn(Optional.of(developpeur));
            simulerSauvegarde();

            assertThat(userService.toggleStatus(2).getActif()).isFalse();
        }

        @Test
        @DisplayName("toggleStatus réactive un compte désactivé")
        void reactivation() {
            developpeur.setActif(false);
            when(utilisateurRepository.findById(2)).thenReturn(Optional.of(developpeur));
            simulerSauvegarde();

            assertThat(userService.toggleStatus(2).getActif()).isTrue();
        }

        @Test
        @DisplayName("updateRole applique le nouveau rôle")
        void changementDeRole() {
            when(utilisateurRepository.findById(1)).thenReturn(Optional.of(demandeur));
            simulerSauvegarde();

            assertThat(userService.updateRole(1, Role.DEVELOPPEUR).getRole()).isEqualTo(Role.DEVELOPPEUR);
        }

        @Test
        @DisplayName("delete refuse un identifiant inexistant")
        void suppressionInexistante() {
            when(utilisateurRepository.existsById(99)).thenReturn(false);

            assertThatThrownBy(() -> userService.delete(99)).isInstanceOf(UserNotFoundException.class);

            verify(utilisateurRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("delete supprime un compte existant")
        void suppressionOk() {
            when(utilisateurRepository.existsById(2)).thenReturn(true);

            userService.delete(2);

            verify(utilisateurRepository).deleteById(2);
        }

        @Test
        @DisplayName("updatePhoto enregistre l'URL de l'avatar")
        void miseAJourPhoto() {
            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(developpeur));
            simulerSauvegarde();

            assertThat(userService.updatePhoto("dev@stb.tn", "/uploads/avatars/a.png").getPhotoUrl())
                    .isEqualTo("/uploads/avatars/a.png");
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("rechercher — liste paginée d'administration")
    class Rechercher {

        @Test
        @DisplayName("la pagination et le tri alphabétique sur le nom sont transmis")
        void paginationEtTri() {
            when(utilisateurRepository.findAll(
                    ArgumentMatchers.<Specification<Utilisateur>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(chef), PageRequest.of(0, 10), 1));

            PageResponse<UtilisateurDto> resultat =
                    userService.rechercher(Role.CHEF_DE_PROJET, true, "mok", 0, 10);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(utilisateurRepository).findAll(
                    ArgumentMatchers.<Specification<Utilisateur>>any(), pageable.capture());

            assertThat(pageable.getValue().getSort().getOrderFor("nom").getDirection())
                    .isEqualTo(Sort.Direction.ASC);
            assertThat(resultat.getContenu()).hasSize(1);
            assertThat(resultat.getTotalElements()).isEqualTo(1);
        }
    }
}
