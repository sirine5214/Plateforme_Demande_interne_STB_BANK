package tn.esprit.stb.back_office.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.Utilisateur;

/** Critères de recherche des utilisateurs pour l'écran d'administration. */
public final class UtilisateurSpecifications {

    private UtilisateurSpecifications() {
    }

    public static Specification<Utilisateur> avecCriteres(Role role, Boolean actif, String motCle) {
        return (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();

            if (role != null) {
                predicats.add(cb.equal(racine.get("role"), role));
            }
            if (actif != null) {
                predicats.add(cb.equal(racine.get("actif"), actif));
            }
            if (motCle != null && !motCle.isBlank()) {
                String motif = "%" + motCle.trim().toLowerCase() + "%";
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(racine.get("email")), motif)));
            }

            return cb.and(predicats.toArray(new Predicate[0]));
        };
    }
}
