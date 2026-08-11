package tn.esprit.stb.back_office.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AffectationRequest {

    @NotNull(message = "Le responsable est obligatoire")
    private Integer responsableId;
}
