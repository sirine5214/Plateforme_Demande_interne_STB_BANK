package tn.esprit.stb.back_office.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;

@Getter
@Setter
@AllArgsConstructor
public class DemandeDto {
    private Integer id;
    private String numero;
    private String titre;
    private String description;
    private Priorite priorite;
    private StatutDemande statut;
    private TypeDemande type;
    private LocalDateTime dateCreation;
    private LocalDate dateLimite;
    private LocalDateTime dateCloture;
    private Integer demandeurId;
    private String demandeurNom;
    private Integer responsableId;
    private String responsableNom;
}
