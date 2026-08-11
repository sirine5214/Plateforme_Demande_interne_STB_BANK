package tn.esprit.stb.back_office.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.stb.back_office.entities.HistoriqueStatut;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Integer> {

    List<HistoriqueStatut> findByDemandeIdOrderByDateChangementDesc(Integer demandeId);
}
