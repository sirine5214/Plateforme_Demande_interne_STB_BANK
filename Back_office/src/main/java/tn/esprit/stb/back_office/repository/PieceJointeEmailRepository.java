package tn.esprit.stb.back_office.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.stb.back_office.entities.PieceJointeEmail;

public interface PieceJointeEmailRepository extends JpaRepository<PieceJointeEmail, Integer> {

    List<PieceJointeEmail> findByEmailEntrantId(Integer emailEntrantId);
}
