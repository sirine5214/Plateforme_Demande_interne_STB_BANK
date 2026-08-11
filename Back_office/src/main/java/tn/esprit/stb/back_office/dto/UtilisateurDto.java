package tn.esprit.stb.back_office.dto;

import java.time.LocalDateTime;

import tn.esprit.stb.back_office.entities.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UtilisateurDto {
    private Integer id;
    private String nom;
    private String email;
    private Role role;
    private String photoUrl;
    private Boolean actif;
    private LocalDateTime dateCreation;
}
