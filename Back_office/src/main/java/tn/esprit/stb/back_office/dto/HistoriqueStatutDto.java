package tn.esprit.stb.back_office.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.StatutDemande;

@Getter
@Setter
@AllArgsConstructor
public class HistoriqueStatutDto {
    private Integer id;
    private StatutDemande ancienStatut;
    private StatutDemande nouveauStatut;
    private LocalDateTime dateChangement;
    private String auteurNom;
}
