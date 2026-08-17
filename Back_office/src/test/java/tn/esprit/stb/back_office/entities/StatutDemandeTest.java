package tn.esprit.stb.back_office.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Le cycle de vie est porté par l'énumération : ces tests le verrouillent sans aucun mock,
 * puisqu'il ne dépend d'aucune infrastructure.
 */
@DisplayName("StatutDemande — cycle de vie d'une demande")
class StatutDemandeTest {

    @Test
    @DisplayName("une demande nouvelle part en traitement ou est rejetée")
    void transitionsDepuisNouvelle() {
        assertThat(StatutDemande.NOUVELLE.transitionsAutorisees())
                .containsExactlyInAnyOrder(StatutDemande.EN_COURS, StatutDemande.REJETEE);
    }

    @Test
    @DisplayName("une demande en cours peut avancer, revenir en arrière ou être rejetée")
    void transitionsDepuisEnCours() {
        assertThat(StatutDemande.EN_COURS.transitionsAutorisees())
                .containsExactlyInAnyOrder(
                        StatutDemande.EN_VALIDATION, StatutDemande.NOUVELLE, StatutDemande.REJETEE);
    }

    @Test
    @DisplayName("une demande en validation est clôturée, renvoyée en correction ou rejetée")
    void transitionsDepuisEnValidation() {
        assertThat(StatutDemande.EN_VALIDATION.transitionsAutorisees())
                .containsExactlyInAnyOrder(
                        StatutDemande.TERMINEE, StatutDemande.EN_COURS, StatutDemande.REJETEE);
    }

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = {"TERMINEE", "REJETEE"})
    @DisplayName("un statut final ne mène nulle part")
    void statutFinalSansSuite(StatutDemande statut) {
        assertThat(statut.estFinal()).isTrue();
        assertThat(statut.transitionsAutorisees()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = {"NOUVELLE", "EN_COURS", "EN_VALIDATION"})
    @DisplayName("un statut ouvert n'est pas final et propose au moins une suite")
    void statutOuvert(StatutDemande statut) {
        assertThat(statut.estFinal()).isFalse();
        assertThat(statut.transitionsAutorisees()).isNotEmpty();
    }

    @Test
    @DisplayName("les sauts d'étape sont refusés")
    void sautsDEtapeRefuses() {
        assertThat(StatutDemande.NOUVELLE.peutAllerVers(StatutDemande.EN_VALIDATION)).isFalse();
        assertThat(StatutDemande.NOUVELLE.peutAllerVers(StatutDemande.TERMINEE)).isFalse();
        assertThat(StatutDemande.EN_COURS.peutAllerVers(StatutDemande.TERMINEE)).isFalse();
    }

    @Test
    @DisplayName("une demande clôturée ne se rouvre jamais")
    void clotureDefinitive() {
        for (StatutDemande cible : StatutDemande.values()) {
            assertThat(StatutDemande.TERMINEE.peutAllerVers(cible)).isFalse();
            assertThat(StatutDemande.REJETEE.peutAllerVers(cible)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(StatutDemande.class)
    @DisplayName("aucun statut ne boucle sur lui-même")
    void pasDeTransitionVersSoiMeme(StatutDemande statut) {
        assertThat(statut.peutAllerVers(statut)).isFalse();
    }

    @Test
    @DisplayName("le rejet reste accessible depuis tous les statuts ouverts")
    void rejetToujoursPossibleAvantCloture() {
        for (StatutDemande statut : EnumSet.of(
                StatutDemande.NOUVELLE, StatutDemande.EN_COURS, StatutDemande.EN_VALIDATION)) {
            assertThat(statut.peutAllerVers(StatutDemande.REJETEE))
                    .as("rejet depuis %s", statut)
                    .isTrue();
        }
    }
}
