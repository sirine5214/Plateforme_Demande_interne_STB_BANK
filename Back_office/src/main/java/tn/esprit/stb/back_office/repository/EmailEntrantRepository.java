package tn.esprit.stb.back_office.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.stb.back_office.entities.EmailEntrant;
import tn.esprit.stb.back_office.entities.StatutEmail;

public interface EmailEntrantRepository extends JpaRepository<EmailEntrant, Integer> {

    /**
     * Verrou d'idempotence de la relève : avant d'insérer un message, le service vérifie
     * qu'il n'est pas déjà connu. La contrainte d'unicité en base reste le filet de sécurité
     * en cas de relèves concurrentes.
     */
    boolean existsByMessageId(String messageId);

    Optional<EmailEntrant> findByMessageId(String messageId);

    /** Boîte de réception : les messages en attente, le plus récent d'abord. */
    Page<EmailEntrant> findByStatutOrderByDateReceptionDesc(StatutEmail statut, Pageable pageable);

    long countByStatut(StatutEmail statut);

    /** Historique des messages reçus d'un même expéditeur, utile lors de la qualification. */
    List<EmailEntrant> findByExpediteurEmailOrderByDateReceptionDesc(String expediteurEmail);
}
