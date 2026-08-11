package tn.esprit.stb.back_office.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PieceJointeDto {
    private Integer id;
    private String nomFichier;
    private String url;
    private Long tailleOctets;
    private LocalDateTime dateAjout;
}
