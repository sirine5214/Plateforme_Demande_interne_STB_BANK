package tn.esprit.stb.back_office.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StatistiquesDto {
    private long total;
    private long ouvertes;
    private long cloturees;
    private Map<String, Long> parStatut;
    private Map<String, Long> parPriorite;
    private Map<String, Long> parResponsable;
    private Map<String, Long> parType;
    /** Demandes non clôturées dont la date limite est dépassée. */
    private long enRetard;
    /** Demandes non clôturées sans responsable désigné. */
    private long nonAffectees;
    /** Nombre de demandes créées par mois (clé « yyyy-MM »), sur les 12 derniers mois. */
    private Map<String, Long> evolutionMensuelle;
    /** Temps moyen entre la création et la clôture, en heures (0 si aucune demande clôturée). */
    private double tempsMoyenTraitementHeures;
}
