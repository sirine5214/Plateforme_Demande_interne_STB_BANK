package tn.esprit.stb.back_office.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.Role;

@Getter
@Setter
public class AdminUpdateUserRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotNull(message = "Le rôle est obligatoire")
    private Role role;

    @NotNull(message = "L'état est obligatoire")
    private Boolean actif;

    // Optionnel : uniquement si l'administrateur souhaite réinitialiser le mot de passe
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String nouveauMotDePasse;
}
