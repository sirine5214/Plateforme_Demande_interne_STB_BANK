package tn.esprit.stb.back_office.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.TypeDemande;

/**
 * Qualification d'un e-mail par un chef de projet ou un administrateur.
 *
 * <p>Le type et la priorité sont obligatoires alors que la plateforme sait les proposer :
 * c'est délibéré. L'agent doit confirmer explicitement une valeur, sinon la proposition
 * automatique deviendrait de fait une décision automatique.
 */
@Getter
@Setter
public class ConversionEmailRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String titre;

    private String description;

    @NotNull(message = "La priorité est obligatoire")
    private Priorite priorite;

    @NotNull(message = "Le type de demande est obligatoire")
    private TypeDemande type;

    private LocalDate dateLimite;

    /**
     * Demandeur à qui rattacher la demande.
     *
     * <p>Facultatif : à défaut, la plateforme tente un rapprochement sur l'adresse de
     * l'expéditeur, et retombe sur l'agent qui qualifie si elle est inconnue. Jamais de
     * création implicite de compte à partir d'une adresse non authentifiée.
     */
    private Integer demandeurId;
}
