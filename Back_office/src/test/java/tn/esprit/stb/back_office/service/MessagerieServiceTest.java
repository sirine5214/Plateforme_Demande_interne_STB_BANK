package tn.esprit.stb.back_office.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.stb.back_office.dto.MessageDto;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.Message;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.entities.TypeNotification;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.AccesRefuseException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.DemandeRepository;
import tn.esprit.stb.back_office.repository.MessageRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.websocket.TempsReelHandler;

/** Tests unitaires du fil de discussion rattaché à une demande. */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessagerieService — fil de discussion")
class MessagerieServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private DemandeRepository demandeRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TempsReelHandler tempsReelHandler;

    @InjectMocks
    private MessagerieService messagerieService;

    private Utilisateur demandeur;
    private Utilisateur responsable;
    private Utilisateur chef;
    private Utilisateur intrus;
    private Demande demande;

    @BeforeEach
    void preparerJeuDEssai() {
        demandeur = utilisateur(1, "Sirine", "demandeur@stb.tn", Role.DEMANDEUR);
        responsable = utilisateur(2, "Wassim", "dev@stb.tn", Role.DEVELOPPEUR);
        chef = utilisateur(3, "Mokhtar", "chef@stb.tn", Role.CHEF_DE_PROJET);
        intrus = utilisateur(4, "Sarra", "sarra@stb.tn", Role.DEVELOPPEUR);

        demande = new Demande();
        demande.setId(10);
        demande.setNumero("DEM-10");
        demande.setTitre("Correction du calcul");
        demande.setPriorite(Priorite.HAUTE);
        demande.setType(TypeDemande.CORRECTION_BUG);
        demande.setStatut(StatutDemande.EN_COURS);
        demande.setDemandeur(demandeur);
        demande.setResponsable(responsable);
    }

    private Utilisateur utilisateur(Integer id, String nom, String email, Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setNom(nom);
        u.setEmail(email);
        u.setRole(role);
        u.setActif(true);
        return u;
    }

    private Message message(Integer id, Utilisateur expediteur, boolean lu) {
        Message m = new Message();
        m.setId(id);
        m.setContenu("Contenu " + id);
        m.setExpediteur(expediteur);
        m.setDemande(demande);
        m.setLu(lu);
        return m;
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("lister — accès au fil")
    class Lister {

        @Test
        @DisplayName("le demandeur consulte le fil de sa demande")
        void demandeurAutorise() {
            when(utilisateurRepository.findByEmail("demandeur@stb.tn")).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.findByDemandeIdOrderByDateEnvoiAsc(10))
                    .thenReturn(List.of(message(1, responsable, false)));

            List<MessageDto> resultat = messagerieService.lister("demandeur@stb.tn", 10);

            assertThat(resultat).hasSize(1);
            assertThat(resultat.get(0).getExpediteurNom()).isEqualTo("Wassim");
            assertThat(resultat.get(0).getDemandeNumero()).isEqualTo("DEM-10");
        }

        @Test
        @DisplayName("le chef de projet accède au fil au titre de la supervision")
        void chefAutorise() {
            when(utilisateurRepository.findByEmail("chef@stb.tn")).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.findByDemandeIdOrderByDateEnvoiAsc(10)).thenReturn(List.of());

            assertThat(messagerieService.lister("chef@stb.tn", 10)).isEmpty();
        }

        @Test
        @DisplayName("un développeur étranger à la demande est refusé")
        void intrusRefuse() {
            when(utilisateurRepository.findByEmail("sarra@stb.tn")).thenReturn(Optional.of(intrus));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> messagerieService.lister("sarra@stb.tn", 10))
                    .isInstanceOf(AccesRefuseException.class)
                    .hasMessageContaining("ne participez pas");
        }

        @Test
        @DisplayName("une demande inexistante est signalée")
        void demandeIntrouvable() {
            when(utilisateurRepository.findByEmail("chef@stb.tn")).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messagerieService.lister("chef@stb.tn", 404))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("Demande introuvable");
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("envoyer — publication d'un message")
    class Envoyer {

        @Test
        @DisplayName("le contenu est nettoyé et le responsable est prévenu")
        void envoiDuDemandeur() {
            when(utilisateurRepository.findByEmail("demandeur@stb.tn")).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

            MessageDto resultat = messagerieService.envoyer("demandeur@stb.tn", 10, "   Bonjour   ");

            assertThat(resultat.getContenu()).isEqualTo("Bonjour");
            assertThat(resultat.getExpediteurId()).isEqualTo(1);

            // L'expéditeur ne se notifie pas lui-même : seul le responsable est prévenu
            verify(tempsReelHandler).envoyer(eq("dev@stb.tn"), eq("MESSAGE"), any(MessageDto.class));
            verify(tempsReelHandler, never()).envoyer(eq("demandeur@stb.tn"), any(), any());
            verify(notificationService).notifier(
                    eq(responsable), eq(demandeur), eq(demande), any(String.class), eq(TypeNotification.MESSAGE));
        }

        @Test
        @DisplayName("le responsable qui répond prévient le demandeur")
        void envoiDuResponsable() {
            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(responsable));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

            messagerieService.envoyer("dev@stb.tn", 10, "C'est en cours");

            verify(tempsReelHandler).envoyer(eq("demandeur@stb.tn"), eq("MESSAGE"), any(MessageDto.class));
            verify(notificationService).notifier(
                    eq(demandeur), eq(responsable), eq(demande), any(String.class), eq(TypeNotification.MESSAGE));
        }

        @Test
        @DisplayName("le chef prévient les deux parties")
        void envoiDuChef() {
            when(utilisateurRepository.findByEmail("chef@stb.tn")).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

            messagerieService.envoyer("chef@stb.tn", 10, "Point d'étape");

            verify(tempsReelHandler).envoyer(eq("demandeur@stb.tn"), eq("MESSAGE"), any(MessageDto.class));
            verify(tempsReelHandler).envoyer(eq("dev@stb.tn"), eq("MESSAGE"), any(MessageDto.class));
        }

        @Test
        @DisplayName("sur une demande non affectée, seul le demandeur est prévenu")
        void demandeSansResponsable() {
            demande.setResponsable(null);
            demande.setStatut(StatutDemande.NOUVELLE);

            when(utilisateurRepository.findByEmail("chef@stb.tn")).thenReturn(Optional.of(chef));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

            messagerieService.envoyer("chef@stb.tn", 10, "Merci de préciser");

            verify(tempsReelHandler).envoyer(eq("demandeur@stb.tn"), eq("MESSAGE"), any(MessageDto.class));
            verify(notificationService).notifier(any(), any(), any(), any(String.class), any());
        }

        @Test
        @DisplayName("un intrus ne peut pas écrire dans le fil")
        void intrusRefuse() {
            when(utilisateurRepository.findByEmail("sarra@stb.tn")).thenReturn(Optional.of(intrus));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));

            assertThatThrownBy(() -> messagerieService.envoyer("sarra@stb.tn", 10, "Coucou"))
                    .isInstanceOf(AccesRefuseException.class);

            verifyNoInteractions(messageRepository, tempsReelHandler, notificationService);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("marquerLus / compterNonLus")
    class Lecture {

        @Test
        @DisplayName("seuls les messages reçus et non lus sont marqués")
        void marquageCible() {
            Message recuNonLu = message(1, responsable, false);
            Message recuDejaLu = message(2, responsable, true);
            Message envoyeParSoi = message(3, demandeur, false);

            when(utilisateurRepository.findByEmail("demandeur@stb.tn")).thenReturn(Optional.of(demandeur));
            when(demandeRepository.findById(10)).thenReturn(Optional.of(demande));
            when(messageRepository.findByDemandeIdOrderByDateEnvoiAsc(10))
                    .thenReturn(List.of(recuNonLu, recuDejaLu, envoyeParSoi));

            messagerieService.marquerLus("demandeur@stb.tn", 10);

            assertThat(recuNonLu.getLu()).isTrue();
            assertThat(envoyeParSoi.getLu()).isFalse();
            verify(messageRepository).saveAll(List.of(recuNonLu));
        }

        @Test
        @DisplayName("le compteur exclut les messages de l'utilisateur lui-même")
        void compteurNonLus() {
            when(utilisateurRepository.findByEmail("demandeur@stb.tn")).thenReturn(Optional.of(demandeur));
            when(messageRepository.countByDemandeIdAndLuFalseAndExpediteurIdNot(10, 1)).thenReturn(2L);

            assertThat(messagerieService.compterNonLus("demandeur@stb.tn", 10)).isEqualTo(2);
        }
    }
}
