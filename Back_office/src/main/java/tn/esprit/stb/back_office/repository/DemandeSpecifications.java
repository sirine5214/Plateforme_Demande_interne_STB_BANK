package tn.esprit.stb.back_office.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;

/** Construit dynamiquement les critères de recherche des demandes (BF 2.6). */
public final class DemandeSpecifications {

    private DemandeSpecifications() {
    }

    public static Specification<Demande> avecCriteres(
            StatutDemande statut,
            Priorite priorite,
            TypeDemande type,
            Integer responsableId,
            Integer demandeurId,
            LocalDateTime dateDebut,
            LocalDateTime dateFin,
            String motCle) {

        return (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();

            if (statut != null) {
                predicats.add(cb.equal(racine.get("statut"), statut));
            }
            if (priorite != null) {
                predicats.add(cb.equal(racine.get("priorite"), priorite));
            }
            if (type != null) {
                predicats.add(cb.equal(racine.get("type"), type));
            }
            if (responsableId != null) {
                predicats.add(cb.equal(racine.get("responsable").get("id"), responsableId));
            }
            if (demandeurId != null) {
                predicats.add(cb.equal(racine.get("demandeur").get("id"), demandeurId));
            }
            if (dateDebut != null) {
                predicats.add(cb.greaterThanOrEqualTo(racine.get("dateCreation"), dateDebut));
            }
            if (dateFin != null) {
                predicats.add(cb.lessThanOrEqualTo(racine.get("dateCreation"), dateFin));
            }
            if (motCle != null && !motCle.isBlank()) {
                String motif = "%" + motCle.trim().toLowerCase() + "%";
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("titre")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("description"), "")), motif),
                        cb.like(cb.lower(racine.get("numero")), motif)));
            }

            return cb.and(predicats.toArray(new Predicate[0]));
        };
    }
}
