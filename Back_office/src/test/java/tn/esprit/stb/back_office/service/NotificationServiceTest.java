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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.stb.back_office.dto.NotificationDto;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.Notification;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.entities.TypeNotification;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.AccesRefuseException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.NotificationRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.websocket.TempsReelHandler;

/** Tests unitaires des notifications : création, poussée temps réel et lecture. */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — notifications")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private TempsReelHandler tempsReelHandler;

    @InjectMocks
    private NotificationService notificationService;

    private Utilisateur destinataire;
    private Utilisateur auteur;
    private Demande demande;

    @BeforeEach
    void preparerJeuDEssai() {
        destinataire = utilisateur(1, "Wassim", "dev@stb.tn");
        auteur = utilisateur(2, "Mokhtar", "chef@stb.tn");

        demande = new Demande();
        demande.setId(10);
        demande.setNumero("DEM-10");
        demande.setTitre("Titre");
        demande.setPriorite(Priorite.MOYENNE);
        demande.setType(TypeDemande.DEVELOPPEMENT);
        demande.setStatut(StatutDemande.NOUVELLE);
        demande.setDemandeur(auteur);
    }

    private Utilisateur utilisateur(Integer id, String nom, String email) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setNom(nom);
        u.setEmail(email);
        u.setRole(Role.DEVELOPPEUR);
        u.setActif(true);
        return u;
    }

    private Notification notification(Integer id, Utilisateur pour, boolean lu) {
        Notification n = new Notification();
        n.setId(id);
        n.setMessage("Message " + id);
        n.setDestinataire(pour);
        n.setAuteur(auteur);
        n.setDemande(demande);
        n.setLu(lu);
        n.setType(TypeNotification.DEMANDE);
        return n;
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("notifier — émission")
    class Notifier {

        @Test
        @DisplayName("la notification est enregistrée puis poussée en temps réel")
        void notificationEnregistreeEtPoussee() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            notificationService.notifier(destinataire, auteur, demande, "Vous avez une demande");

            ArgumentCaptor<Notification> enregistree = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(enregistree.capture());
            assertThat(enregistree.getValue().getMessage()).isEqualTo("Vous avez une demande");
            assertThat(enregistree.getValue().getType()).isEqualTo(TypeNotification.DEMANDE);

            verify(tempsReelHandler).envoyer(eq("dev@stb.tn"), eq("NOTIFICATION"), any(NotificationDto.class));
        }

        @Test
        @DisplayName("le type MESSAGE est conservé quand il est précisé")
        void typeMessagePrecise() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            notificationService.notifier(destinataire, auteur, demande, "Nouveau message", TypeNotification.MESSAGE);

            ArgumentCaptor<Notification> enregistree = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(enregistree.capture());
            assertThat(enregistree.getValue().getType()).isEqualTo(TypeNotification.MESSAGE);
        }

        @Test
        @DisplayName("un destinataire absent est ignoré sans erreur")
        void destinataireNul() {
            notificationService.notifier(null, auteur, demande, "Sans destinataire");

            verifyNoInteractions(notificationRepository, tempsReelHandler);
        }

        @Test
        @DisplayName("on ne se notifie pas soi-même")
        void pasDAutoNotification() {
            notificationService.notifier(auteur, auteur, demande, "Action personnelle");

            verifyNoInteractions(notificationRepository, tempsReelHandler);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("lecture des notifications")
    class Lecture {

        @Test
        @DisplayName("la liste est cadrée sur le destinataire connecté")
        void listeDuDestinataire() {
            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.findByDestinataireIdOrderByDateEnvoiDesc(1))
                    .thenReturn(List.of(notification(100, destinataire, false)));

            List<NotificationDto> resultat = notificationService.lister("dev@stb.tn");

            assertThat(resultat).hasSize(1);
            assertThat(resultat.get(0).getDemandeNumero()).isEqualTo("DEM-10");
            assertThat(resultat.get(0).getAuteurNom()).isEqualTo("Mokhtar");
        }

        @Test
        @DisplayName("une notification sans auteur est attribuée au « Système »")
        void auteurSysteme() {
            Notification sansAuteur = notification(101, destinataire, false);
            sansAuteur.setAuteur(null);
            sansAuteur.setDemande(null);

            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.findByDestinataireIdOrderByDateEnvoiDesc(1))
                    .thenReturn(List.of(sansAuteur));

            NotificationDto dto = notificationService.lister("dev@stb.tn").get(0);

            assertThat(dto.getAuteurNom()).isEqualTo("Système");
            assertThat(dto.getDemandeId()).isNull();
        }

        @Test
        @DisplayName("le compteur des non lues interroge le bon destinataire")
        void compteurNonLues() {
            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.countByDestinataireIdAndLuFalse(1)).thenReturn(3L);

            assertThat(notificationService.compterNonLues("dev@stb.tn")).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("marquage comme lu")
    class Marquage {

        @Test
        @DisplayName("le destinataire marque sa notification comme lue")
        void marquageAutorise() {
            Notification sienne = notification(100, destinataire, false);

            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.findById(100)).thenReturn(Optional.of(sienne));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(notificationService.marquerCommeLue("dev@stb.tn", 100).getLu()).isTrue();
        }

        @Test
        @DisplayName("marquer la notification d'un autre est refusé")
        void marquageRefuse() {
            Notification celleDunAutre = notification(101, auteur, false);

            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.findById(101)).thenReturn(Optional.of(celleDunAutre));

            assertThatThrownBy(() -> notificationService.marquerCommeLue("dev@stb.tn", 101))
                    .isInstanceOf(AccesRefuseException.class);

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("une notification inexistante est signalée")
        void notificationIntrouvable() {
            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.marquerCommeLue("dev@stb.tn", 404))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("tout marquer comme lu bascule uniquement les non lues")
        void toutMarquerCommeLu() {
            Notification premiere = notification(100, destinataire, false);
            Notification seconde = notification(101, destinataire, false);

            when(utilisateurRepository.findByEmail("dev@stb.tn")).thenReturn(Optional.of(destinataire));
            when(notificationRepository.findByDestinataireIdAndLuFalseOrderByDateEnvoiDesc(1))
                    .thenReturn(List.of(premiere, seconde));

            notificationService.toutMarquerCommeLu("dev@stb.tn");

            assertThat(premiere.getLu()).isTrue();
            assertThat(seconde.getLu()).isTrue();
            verify(notificationRepository).saveAll(List.of(premiere, seconde));
        }
    }
}
