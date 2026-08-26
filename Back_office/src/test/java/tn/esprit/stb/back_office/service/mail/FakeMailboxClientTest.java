package tn.esprit.stb.back_office.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests unitaires de la boîte aux lettres en mémoire utilisée hors serveur de messagerie. */
@DisplayName("FakeMailboxClient — boîte aux lettres en mémoire")
class FakeMailboxClientTest {

    private FakeMailboxClient boite;

    @BeforeEach
    void preparerBoite() {
        boite = new FakeMailboxClient();
    }

    private static EmailBrut message(String identifiant, LocalDateTime dateReception) {
        return new EmailBrut(
                identifiant,
                "expediteur@stb.com.tn",
                "Expediteur Test",
                "Objet de test",
                "Corps de test",
                null,
                dateReception,
                List.of());
    }

    @Nested
    @DisplayName("Relève des messages")
    class ReleveDesMessages {

        @Test
        @DisplayName("fournit un jeu de démonstration dès la construction")
        void fournitUnJeuDeDemonstration() {
            assertThat(boite.recupererNouveauxMessages(10)).hasSize(3);
        }

        @Test
        @DisplayName("rend les messages du plus ancien au plus récent")
        void rendLesMessagesDuPlusAncienAuPlusRecent() {
            boite.vider();
            LocalDateTime maintenant = LocalDateTime.now();
            boite.deposer(message("recent", maintenant));
            boite.deposer(message("ancien", maintenant.minusDays(1)));

            List<EmailBrut> releve = boite.recupererNouveauxMessages(10);

            assertThat(releve).extracting(EmailBrut::messageId)
                    .containsExactly("ancien", "recent");
        }

        @Test
        @DisplayName("respecte la limite demandée")
        void respecteLaLimiteDemandee() {
            assertThat(boite.recupererNouveauxMessages(2)).hasSize(2);
        }

        @Test
        @DisplayName("ne rend rien pour une limite nulle ou négative")
        void neRendRienPourUneLimiteNulle() {
            assertThat(boite.recupererNouveauxMessages(0)).isEmpty();
            assertThat(boite.recupererNouveauxMessages(-1)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Marquage des messages traités")
    class MarquageDesMessagesTraites {

        @Test
        @DisplayName("écarte des relèves suivantes un message marqué comme traité")
        void ecarteUnMessageMarqueCommeTraite() {
            boite.vider();
            boite.deposer(message("msg-1", LocalDateTime.now()));

            boite.marquerCommeTraite("msg-1");

            assertThat(boite.recupererNouveauxMessages(10)).isEmpty();
        }

        @Test
        @DisplayName("reste sans effet pour un identifiant inconnu")
        void resteSansEffetPourUnIdentifiantInconnu() {
            boite.marquerCommeTraite("identifiant-inexistant");

            assertThat(boite.recupererNouveauxMessages(10)).hasSize(3);
        }

        @Test
        @DisplayName("est idempotent : marquer deux fois ne change rien")
        void estIdempotent() {
            boite.vider();
            boite.deposer(message("msg-1", LocalDateTime.now()));

            boite.marquerCommeTraite("msg-1");
            boite.marquerCommeTraite("msg-1");

            assertThat(boite.recupererNouveauxMessages(10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Construction d'un message brut")
    class ConstructionDUnMessageBrut {

        @Test
        @DisplayName("remplace une liste de pièces jointes nulle par une liste vide")
        void remplaceUneListeNulleParUneListeVide() {
            EmailBrut sansPiecesJointes = new EmailBrut(
                    "msg-sans-pj", "a@stb.com.tn", "A", "Objet", "Corps", null,
                    LocalDateTime.now(), null);

            assertThat(sansPiecesJointes.piecesJointes()).isEmpty();
        }
    }
}
