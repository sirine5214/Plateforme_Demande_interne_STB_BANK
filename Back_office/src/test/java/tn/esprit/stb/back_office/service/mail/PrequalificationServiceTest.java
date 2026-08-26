package tn.esprit.stb.back_office.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.TypeDemande;

/** Tests unitaires des règles de pré-qualification des e-mails entrants. */
@DisplayName("PrequalificationService — proposition de type et de priorité")
class PrequalificationServiceTest {

    private PrequalificationService service;

    @BeforeEach
    void preparerService() {
        service = new PrequalificationService();
    }

    @Nested
    @DisplayName("Déduction du type de demande")
    class DeductionDuType {

        @Test
        @DisplayName("reconnaît une demande de création d'accès")
        void reconnaitUneCreationDAcces() {
            TypeDemande type = service.deduireType(
                    "Demande d'acces a l'application",
                    "Merci de me creer un compte avec les habilitations de conseiller.");

            assertThat(type).isEqualTo(TypeDemande.CREATION_ACCES);
        }

        @Test
        @DisplayName("reconnaît une anomalie")
        void reconnaitUneAnomalie() {
            TypeDemande type = service.deduireType(
                    "Anomalie sur l'edition des releves",
                    "L'ecran renvoie une erreur depuis ce matin.");

            assertThat(type).isEqualTo(TypeDemande.CORRECTION_BUG);
        }

        @Test
        @DisplayName("reconnaît une évolution")
        void reconnaitUneEvolution() {
            TypeDemande type = service.deduireType(
                    "Evolution du module de reporting",
                    "Serait-il possible d'ajouter un export Excel ?");

            assertThat(type).isEqualTo(TypeDemande.EVOLUTION);
        }

        @Test
        @DisplayName("ignore les accents et la casse")
        void ignoreLesAccentsEtLaCasse() {
            TypeDemande type = service.deduireType(
                    "ÉVOLUTION SOUHAITÉE",
                    "Merci d'étudier cette demande.");

            assertThat(type).isEqualTo(TypeDemande.EVOLUTION);
        }

        @Test
        @DisplayName("applique la règle la plus spécifique quand plusieurs correspondent")
        void appliqueLaRegleLaPlusSpecifique() {
            // « acces » et « erreur » figurent tous deux dans le texte : la création d'accès,
            // déclarée en premier, doit l'emporter sur la correction d'anomalie.
            TypeDemande type = service.deduireType(
                    "Probleme d'acces",
                    "J'obtiens une erreur quand j'essaie de me connecter, mes droits "
                            + "semblent incomplets.");

            assertThat(type).isEqualTo(TypeDemande.CREATION_ACCES);
        }

        @Test
        @DisplayName("retombe sur ASSISTANCE quand aucun mot-clé ne correspond")
        void retombeSurAssistance() {
            TypeDemande type = service.deduireType(
                    "Question",
                    "Bonjour, pourriez-vous me rappeler quand vous avez un moment ?");

            assertThat(type).isEqualTo(TypeDemande.ASSISTANCE);
        }

        @Test
        @DisplayName("tolère un objet ou un corps absent")
        void tolereUnTexteAbsent() {
            assertThat(service.deduireType(null, null)).isEqualTo(TypeDemande.ASSISTANCE);
            assertThat(service.deduireType("Demande d'habilitation", null))
                    .isEqualTo(TypeDemande.CREATION_ACCES);
            assertThat(service.deduireType(null, "Signalement d'un bug bloquant"))
                    .isEqualTo(TypeDemande.CORRECTION_BUG);
        }
    }

    @Nested
    @DisplayName("Déduction de la priorité")
    class DeductionDeLaPriorite {

        @Test
        @DisplayName("classe en CRITIQUE un incident de production")
        void classeEnCritiqueUnIncidentDeProduction() {
            Priorite priorite = service.deduirePriorite(
                    "Erreur en production",
                    "Le service est bloquant pour toutes les agences.");

            assertThat(priorite).isEqualTo(Priorite.CRITIQUE);
        }

        @Test
        @DisplayName("classe en HAUTE une demande urgente sans impact production")
        void classeEnHauteUneDemandeUrgente() {
            Priorite priorite = service.deduirePriorite(
                    "URGENT - habilitation a creer",
                    "Le collaborateur arrive lundi, merci de traiter rapidement.");

            assertThat(priorite).isEqualTo(Priorite.HAUTE);
        }

        @Test
        @DisplayName("fait primer la production sur l'urgence déclarée")
        void faitPrimerLaProductionSurLUrgence() {
            Priorite priorite = service.deduirePriorite(
                    "Urgent",
                    "Incident en production depuis 10h.");

            assertThat(priorite).isEqualTo(Priorite.CRITIQUE);
        }

        @Test
        @DisplayName("classe en BASSE une demande explicitement non urgente")
        void classeEnBasseUneDemandeNonUrgente() {
            Priorite priorite = service.deduirePriorite(
                    "Suggestion",
                    "Ce n'est pas urgent, c'est un confort d'utilisation.");

            assertThat(priorite).isEqualTo(Priorite.BASSE);
        }

        @Test
        @DisplayName("retombe sur MOYENNE en l'absence d'indice")
        void retombeSurMoyenne() {
            Priorite priorite = service.deduirePriorite(
                    "Demande diverse",
                    "Bonjour, merci de prendre en charge le dossier ci-joint.");

            assertThat(priorite).isEqualTo(Priorite.MOYENNE);
        }
    }
}
