package tn.esprit.stb.back_office.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.HistoriqueStatut;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.repository.DemandeRepository;
import tn.esprit.stb.back_office.repository.HistoriqueStatutRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;

/**
 * Charge un jeu de données de démonstration au premier démarrage.
 * N'agit que si la base est vide : relancer l'application ne duplique rien.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataLoader {

    private static final String MOT_DE_PASSE_DEMO = "Password123";

    @Bean
    ApplicationRunner chargerDonneesInitiales(
            UtilisateurRepository utilisateurRepository,
            DemandeRepository demandeRepository,
            HistoriqueStatutRepository historiqueStatutRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            if (utilisateurRepository.count() > 0) {
                log.info("Base déjà peuplée : chargement des données de démonstration ignoré");
                return;
            }

            log.info("Base vide : chargement du jeu de données de démonstration");

            Utilisateur admin = creerUtilisateur(passwordEncoder, "Admin STB", "admin@stb.tn", Role.ADMINISTRATEUR);
            Utilisateur chef = creerUtilisateur(passwordEncoder, "Mokhtar Ben Ali", "chef@stb.tn", Role.CHEF_DE_PROJET);
            Utilisateur dev1 = creerUtilisateur(passwordEncoder, "Wassim Trabelsi", "dev1@stb.tn", Role.DEVELOPPEUR);
            Utilisateur dev2 = creerUtilisateur(passwordEncoder, "Sarra Gharbi", "dev2@stb.tn", Role.DEVELOPPEUR);
            Utilisateur demandeur1 = creerUtilisateur(passwordEncoder, "Sirine Ben Cheikh", "demandeur@stb.tn", Role.DEMANDEUR);
            Utilisateur demandeur2 = creerUtilisateur(passwordEncoder, "Karim Jebali", "demandeur2@stb.tn", Role.DEMANDEUR);

            utilisateurRepository.saveAll(List.of(admin, chef, dev1, dev2, demandeur1, demandeur2));

            // Demandes couvrant tous les statuts et priorités, réparties sur plusieurs mois
            creerDemande(demandeRepository, historiqueStatutRepository,
                    "Ajout de l'authentification à deux facteurs", TypeDemande.DEVELOPPEMENT, Priorite.HAUTE,
                    StatutDemande.NOUVELLE, demandeur1, null, 0, false);

            creerDemande(demandeRepository, historiqueStatutRepository,
                    "Correction du calcul des intérêts sur les comptes épargne", TypeDemande.CORRECTION_BUG, Priorite.CRITIQUE,
                    StatutDemande.EN_COURS, demandeur1, dev1, 5, false);

            creerDemande(demandeRepository, historiqueStatutRepository,
                    "Accès à l'application de reporting pour le service crédit", TypeDemande.CREATION_ACCES, Priorite.MOYENNE,
                    StatutDemande.EN_VALIDATION, demandeur2, dev2, 12, false);

            creerDemande(demandeRepository, historiqueStatutRepository,
                    "Migration de la base vers PostgreSQL 16", TypeDemande.MAINTENANCE, Priorite.HAUTE,
                    StatutDemande.TERMINEE, demandeur2, dev1, 40, true);

            creerDemande(demandeRepository, historiqueStatutRepository,
                    "Assistance sur l'export des relevés clients", TypeDemande.ASSISTANCE, Priorite.BASSE,
                    StatutDemande.TERMINEE, demandeur1, dev2, 65, true);

            creerDemande(demandeRepository, historiqueStatutRepository,
                    "Refonte de l'écran de virement", TypeDemande.EVOLUTION, Priorite.MOYENNE,
                    StatutDemande.REJETEE, demandeur2, dev1, 80, true);

            log.info("Jeu de données chargé : 6 utilisateurs et 6 demandes (mot de passe commun : {})", MOT_DE_PASSE_DEMO);
        };
    }

    private Utilisateur creerUtilisateur(PasswordEncoder encoder, String nom, String email, Role role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(nom);
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(encoder.encode(MOT_DE_PASSE_DEMO));
        utilisateur.setRole(role);
        utilisateur.setActif(true);
        utilisateur.setDateCreation(LocalDateTime.now());
        return utilisateur;
    }

    private void creerDemande(
            DemandeRepository demandeRepository,
            HistoriqueStatutRepository historiqueStatutRepository,
            String titre,
            TypeDemande type,
            Priorite priorite,
            StatutDemande statut,
            Utilisateur demandeur,
            Utilisateur responsable,
            int joursDansLePasse,
            boolean cloturee) {

        LocalDateTime creation = LocalDateTime.now().minusDays(joursDansLePasse);

        Demande demande = new Demande();
        demande.setNumero("DEM-" + System.nanoTime());
        demande.setTitre(titre);
        demande.setDescription("Demande de démonstration générée automatiquement au premier démarrage.");
        demande.setType(type);
        demande.setPriorite(priorite);
        demande.setStatut(statut);
        demande.setDemandeur(demandeur);
        demande.setResponsable(responsable);
        demande.setDateCreation(creation);
        demande.setDateLimite(LocalDate.now().plusDays(15));

        if (cloturee) {
            demande.setDateCloture(creation.plusDays(3));
        }

        demande = demandeRepository.save(demande);

        HistoriqueStatut historique = new HistoriqueStatut();
        historique.setDemande(demande);
        historique.setAncienStatut(null);
        historique.setNouveauStatut(StatutDemande.NOUVELLE);
        historique.setAuteur(demandeur);
        historique.setDateChangement(creation);
        historiqueStatutRepository.save(historique);

        if (statut != StatutDemande.NOUVELLE) {
            HistoriqueStatut transition = new HistoriqueStatut();
            transition.setDemande(demande);
            transition.setAncienStatut(StatutDemande.NOUVELLE);
            transition.setNouveauStatut(statut);
            transition.setAuteur(responsable != null ? responsable : demandeur);
            transition.setDateChangement(creation.plusDays(1));
            historiqueStatutRepository.save(transition);
        }
    }
}
