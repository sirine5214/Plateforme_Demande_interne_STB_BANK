package tn.esprit.stb.back_office.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.StatutDemande;

@Getter
@Setter
public class UpdateStatutRequest {

    @NotNull(message = "Le statut est obligatoire")
    private StatutDemande statut;
}
