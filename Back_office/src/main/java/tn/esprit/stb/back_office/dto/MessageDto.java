package tn.esprit.stb.back_office.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Integer id;
    private String contenu;
    private LocalDateTime dateEnvoi;
    private Boolean lu;
    private Integer expediteurId;
    private String expediteurNom;
    private String expediteurPhotoUrl;
    private Integer demandeId;
    private String demandeNumero;
}
