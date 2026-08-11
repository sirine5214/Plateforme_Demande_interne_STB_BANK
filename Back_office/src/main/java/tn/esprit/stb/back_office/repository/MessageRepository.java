package tn.esprit.stb.back_office.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.stb.back_office.entities.Message;

public interface MessageRepository extends JpaRepository<Message, Integer> {

    List<Message> findByDemandeIdOrderByDateEnvoiAsc(Integer demandeId);

    long countByDemandeIdAndLuFalseAndExpediteurIdNot(Integer demandeId, Integer utilisateurId);
}
