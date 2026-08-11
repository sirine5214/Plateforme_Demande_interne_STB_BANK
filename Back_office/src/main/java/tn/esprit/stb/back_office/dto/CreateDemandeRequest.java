package tn.esprit.stb.back_office.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.TypeDemande;

@Getter
@Setter
public class CreateDemandeRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String titre;

    private String description;

    @NotNull(message = "La priorité est obligatoire")
    private Priorite priorite;

    @NotNull(message = "Le type de demande est obligatoire")
    private TypeDemande type;

    private LocalDate dateLimite;
}
