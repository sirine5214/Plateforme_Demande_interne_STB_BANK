package tn.esprit.stb.back_office.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.StatutDemande;

/**
 * La recherche multicritère passe par {@link JpaSpecificationExecutor} : les critères étant
 * tous optionnels, une requête JPQL avec « :param IS NULL OR ... » est fragile côté Hibernate.
 */
public interface DemandeRepository extends JpaRepository<Demande, Integer>, JpaSpecificationExecutor<Demande> {

    List<Demande> findByDemandeurIdOrderByDateCreationDesc(Integer demandeurId);

    List<Demande> findByResponsableIdOrderByDateCreationDesc(Integer responsableId);

    List<Demande> findAllByOrderByDateCreationDesc();

    long countByStatut(StatutDemande statut);
}
