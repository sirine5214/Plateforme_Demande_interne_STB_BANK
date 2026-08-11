package tn.esprit.stb.back_office.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.Role;

@Getter
@Setter
public class UpdateRoleRequest {

    @NotNull(message = "Le rôle est obligatoire")
    private Role role;
}
