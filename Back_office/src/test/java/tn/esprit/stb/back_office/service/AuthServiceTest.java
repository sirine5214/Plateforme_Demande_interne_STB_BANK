package tn.esprit.stb.back_office.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import tn.esprit.stb.back_office.dto.AuthResponse;
import tn.esprit.stb.back_office.dto.LoginRequest;
import tn.esprit.stb.back_office.dto.RegisterRequest;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.EmailAlreadyUsedException;
import tn.esprit.stb.back_office.exception.JetonInvalideException;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.security.JwtService;
import tn.esprit.stb.back_office.security.UtilisateurDetailsService;

/** Tests unitaires de l'authentification : inscription, connexion, réinitialisation du mot de passe. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — authentification")
class AuthServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UtilisateurDetailsService utilisateurDetailsService;

    @InjectMocks
    private AuthService authService;

    private Utilisateur utilisateur;
    private UserDetails details;

    @BeforeEach
    void preparerJeuDEssai() {
        utilisateur = new Utilisateur();
        utilisateur.setId(1);
        utilisateur.setNom("Sirine");
        utilisateur.setEmail("sirine@stb.tn");
        utilisateur.setMotDePasse("hash");
        utilisateur.setRole(Role.DEMANDEUR);
        utilisateur.setActif(true);

        details = User.withUsername("sirine@stb.tn").password("hash").authorities("ROLE_DEMANDEUR").build();
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("register — inscription")
    class Register {

        private RegisterRequest requete() {
            RegisterRequest requete = new RegisterRequest();
            requete.setNom("Sirine");
            requete.setEmail("sirine@stb.tn");
            requete.setMotDePasse("Password123");
            requete.setRole(Role.DEMANDEUR);
            return requete;
        }

        @Test
        @DisplayName("l'inscription encode le mot de passe et renvoie un jeton")
        void inscriptionReussie() {
            when(utilisateurRepository.existsByEmail("sirine@stb.tn")).thenReturn(false);
            when(passwordEncoder.encode("Password123")).thenReturn("hash");
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(i -> {
                Utilisateur enregistre = i.getArgument(0);
                enregistre.setId(1);
                return enregistre;
            });
            when(utilisateurDetailsService.loadUserByUsername("sirine@stb.tn")).thenReturn(details);
            when(jwtService.generateToken(details)).thenReturn("jeton-jwt");

            AuthResponse resultat = authService.register(requete());

            assertThat(resultat.getToken()).isEqualTo("jeton-jwt");
            assertThat(resultat.getEmail()).isEqualTo("sirine@stb.tn");
            assertThat(resultat.getRole()).isEqualTo(Role.DEMANDEUR);

            ArgumentCaptor<Utilisateur> enregistre = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(enregistre.capture());
            assertThat(enregistre.getValue().getMotDePasse())
                    .as("le mot de passe ne doit jamais être stocké en clair")
                    .isEqualTo("hash");
        }

        @Test
        @DisplayName("un email déjà inscrit est refusé")
        void emailDejaInscrit() {
            when(utilisateurRepository.existsByEmail("sirine@stb.tn")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(requete()))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verify(utilisateurRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("login — connexion")
    class Login {

        private LoginRequest requete() {
            LoginRequest requete = new LoginRequest();
            requete.setEmail("sirine@stb.tn");
            requete.setMotDePasse("Password123");
            return requete;
        }

        @Test
        @DisplayName("la connexion délègue la vérification puis émet un jeton")
        void connexionReussie() {
            when(utilisateurRepository.findByEmail("sirine@stb.tn")).thenReturn(Optional.of(utilisateur));
            when(utilisateurDetailsService.loadUserByUsername("sirine@stb.tn")).thenReturn(details);
            when(jwtService.generateToken(details)).thenReturn("jeton-jwt");

            AuthResponse resultat = authService.login(requete());

            assertThat(resultat.getToken()).isEqualTo("jeton-jwt");
            assertThat(resultat.getId()).isEqualTo(1);
            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        }

        @Test
        @DisplayName("des identifiants erronés remontent l'échec sans émettre de jeton")
        void identifiantsErrones() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Identifiants invalides"));

            assertThatThrownBy(() -> authService.login(requete()))
                    .isInstanceOf(BadCredentialsException.class);

            verify(jwtService, never()).generateToken(any());
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("réinitialisation du mot de passe")
    class Reinitialisation {

        @Test
        @DisplayName("un jeton valable 30 minutes est généré et enregistré")
        void jetonGenere() {
            when(utilisateurRepository.findByEmail("sirine@stb.tn")).thenReturn(Optional.of(utilisateur));

            String jeton = authService.demanderReinitialisation("sirine@stb.tn");

            assertThat(jeton).isNotBlank();
            assertThat(utilisateur.getResetToken()).isEqualTo(jeton);
            assertThat(utilisateur.getResetTokenExpiration()).isAfter(LocalDateTime.now().plusMinutes(29));
            verify(utilisateurRepository).save(utilisateur);
        }

        @Test
        @DisplayName("un email inconnu ne révèle rien et ne lève pas d'erreur")
        void emailInconnuSilencieux() {
            when(utilisateurRepository.findByEmail("inconnu@stb.tn")).thenReturn(Optional.empty());

            assertThat(authService.demanderReinitialisation("inconnu@stb.tn")).isNull();

            verify(utilisateurRepository, never()).save(any());
        }

        @Test
        @DisplayName("un jeton valide réinitialise le mot de passe et se consomme")
        void reinitialisationReussie() {
            utilisateur.setResetToken("jeton-ok");
            utilisateur.setResetTokenExpiration(LocalDateTime.now().plusMinutes(10));

            when(utilisateurRepository.findByResetToken("jeton-ok")).thenReturn(Optional.of(utilisateur));
            when(passwordEncoder.encode("Nouveau123")).thenReturn("hash-nouveau");

            authService.reinitialiserMotDePasse("jeton-ok", "Nouveau123");

            assertThat(utilisateur.getMotDePasse()).isEqualTo("hash-nouveau");
            assertThat(utilisateur.getResetToken()).isNull();
            assertThat(utilisateur.getResetTokenExpiration()).isNull();
        }

        @Test
        @DisplayName("un jeton inconnu est rejeté")
        void jetonInconnu() {
            when(utilisateurRepository.findByResetToken("faux")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.reinitialiserMotDePasse("faux", "Nouveau123"))
                    .isInstanceOf(JetonInvalideException.class)
                    .hasMessageContaining("invalide");
        }

        @Test
        @DisplayName("un jeton expiré est rejeté et le mot de passe reste inchangé")
        void jetonExpire() {
            utilisateur.setResetToken("jeton-vieux");
            utilisateur.setResetTokenExpiration(LocalDateTime.now().minusMinutes(1));

            when(utilisateurRepository.findByResetToken("jeton-vieux")).thenReturn(Optional.of(utilisateur));

            assertThatThrownBy(() -> authService.reinitialiserMotDePasse("jeton-vieux", "Nouveau123"))
                    .isInstanceOf(JetonInvalideException.class)
                    .hasMessageContaining("expiré");

            assertThat(utilisateur.getMotDePasse()).isEqualTo("hash");
            verify(utilisateurRepository, never()).save(any());
        }

        @Test
        @DisplayName("un jeton sans date d'expiration est traité comme invalide")
        void jetonSansExpiration() {
            utilisateur.setResetToken("jeton-orphelin");
            utilisateur.setResetTokenExpiration(null);

            when(utilisateurRepository.findByResetToken("jeton-orphelin")).thenReturn(Optional.of(utilisateur));

            assertThatThrownBy(() -> authService.reinitialiserMotDePasse("jeton-orphelin", "Nouveau123"))
                    .isInstanceOf(JetonInvalideException.class);
        }
    }
}
