package tn.esprit.stb.back_office.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.StatutEmail;
import tn.esprit.stb.back_office.entities.TypeDemande;

/**
 * E-mail entrant tel qu'affiché dans la boîte de réception du back-office.
 *
 * <p>Le corps est exposé en texte brut uniquement : aucun HTML n'est conservé en base,
 * donc aucun balisage d'expéditeur ne peut atteindre le navigateur.
 */
@Getter
@Setter
@AllArgsConstructor
public class EmailEntrantDto {

    private Integer id;
    private String expediteurEmail;
    private String expediteurNom;
    private String sujet;
    private String corpsTexte;
    private LocalDateTime dateReception;
    private StatutEmail statut;

    /** Type déduit automatiquement, présenté comme suggestion à l'écran de qualification. */
    private TypeDemande typePropose;

    /** Priorité déduite automatiquement, également soumise à confirmation. */
    private Priorite prioriteProposee;

    /** Numéro de la demande issue de la conversion, nul tant que l'e-mail est en attente. */
    private String numeroDemande;

    private Integer demandeId;

    /** Agent ayant qualifié le message. */
    private String traiteParNom;

    private LocalDateTime dateTraitement;

    private String motifIgnore;

    /**
     * Utilisateur reconnu à partir de l'adresse d'expédition, s'il existe.
     *
     * <p>Sert uniquement à pré-remplir le formulaire de qualification. L'adresse n'étant pas
     * authentifiée, ce rapprochement reste une suggestion.
     */
    private Integer demandeurSuggereId;

    private String demandeurSuggereNom;

    private List<PieceJointeEmailDto> piecesJointes;
}
