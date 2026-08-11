package tn.esprit.stb.back_office.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.entities.TypeNotification;

@Getter
@Setter
@AllArgsConstructor
public class NotificationDto {
    private Integer id;
    private String message;
    private LocalDateTime dateEnvoi;
    private Boolean lu;
    /** Détermine l'icône affichée et la destination au clic. */
    private TypeNotification type;
    private Integer demandeId;
    private String demandeNumero;
    /** Type de la demande concernée, pour affichage sous forme de badge. */
    private TypeDemande typeDemande;
    /** Nom de l'utilisateur à l'origine de l'événement. */
    private String auteurNom;
}
