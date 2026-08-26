package tn.esprit.stb.back_office.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer>, JpaSpecificationExecutor<Utilisateur> {

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Utilisateur> findByResetToken(String resetToken);

    /**
     * Destinataires des alertes de boite partagee : administrateurs et chefs de projet sont
     * les seuls habilites a qualifier un e-mail entrant.
     */
    List<Utilisateur> findByRole(Role role);
}
