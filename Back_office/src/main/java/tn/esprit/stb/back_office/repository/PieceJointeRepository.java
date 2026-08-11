package tn.esprit.stb.back_office.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.stb.back_office.entities.PieceJointe;

public interface PieceJointeRepository extends JpaRepository<PieceJointe, Integer> {

    List<PieceJointe> findByDemandeIdOrderByDateAjoutDesc(Integer demandeId);
}
