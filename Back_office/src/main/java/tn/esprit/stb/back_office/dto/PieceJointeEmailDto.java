package tn.esprit.stb.back_office.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Pièce jointe d'un e-mail entrant, telle qu'exposée au front-office. */
@Getter
@Setter
@AllArgsConstructor
public class PieceJointeEmailDto {

    private Integer id;
    private String nomFichierOrigine;
    private String cheminFichier;
    private String contentType;
    private Long tailleOctets;
}
