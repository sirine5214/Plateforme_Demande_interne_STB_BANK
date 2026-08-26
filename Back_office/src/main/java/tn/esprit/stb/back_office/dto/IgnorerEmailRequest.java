package tn.esprit.stb.back_office.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Mise à l'écart d'un e-mail : le motif est exigé pour que la décision reste auditable. */
@Getter
@Setter
public class IgnorerEmailRequest {

    @NotBlank(message = "Le motif est obligatoire")
    private String motif;
}
