package tn.esprit.stb.back_office.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.entities.Demande;
import tn.esprit.stb.back_office.entities.HistoriqueStatut;
import tn.esprit.stb.back_office.entities.Message;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.repository.DemandeRepository;
import tn.esprit.stb.back_office.repository.HistoriqueStatutRepository;
import tn.esprit.stb.back_office.repository.MessageRepository;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;

/**
 * Charge le scénario de démonstration de la Direction Développement Digital.
 *
 * <p>Le jeu couvre volontairement l'intégralité des cas que la plateforme sait traiter :
 * les quatre rôles, les six types de demande, les quatre priorités, les cinq statuts, des
 * demandes clôturées comme en attente d'affectation, et des fils de discussion. C'est ce qui
 * permet à chaque écran — listes, filtres, statistiques, messagerie — d'être démontré sans
 * avoir à saisir quoi que ce soit au préalable.
 *
 * <p>Les demandes sont réparties sur six mois afin que la courbe d'évolution mensuelle du
 * tableau de bord ait une forme réaliste plutôt qu'un pic unique.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataLoader {

    private static final String MOT_DE_PASSE_DEMO = "Password123";

    /**
     * Remise à zéro avant chargement.
     *
     * <p>Destructif : efface les demandes, l'historique, les messages et les comptes existants.
     * Réservé au poste de développement et à la préparation d'une démonstration, d'où la
     * valeur par défaut {@code false} — un démarrage ordinaire ne peut pas détruire de données.
     */
    @Value("${stb.demo.reinitialiser:false}")
    private boolean reinitialiser;

    @Bean
    ApplicationRunner chargerDonneesInitiales(
            UtilisateurRepository utilisateurRepository,
            DemandeRepository demandeRepository,
            HistoriqueStatutRepository historiqueStatutRepository,
            MessageRepository messageRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            if (reinitialiser) {
                log.warn("stb.demo.reinitialiser=true : suppression des donnees existantes");
                messageRepository.deleteAll();
                historiqueStatutRepository.deleteAll();
                demandeRepository.deleteAll();
                utilisateurRepository.deleteAll();
            } else if (utilisateurRepository.count() > 0) {
                log.info("Base deja peuplee : chargement du scenario de demonstration ignore");
                return;
            }

            log.info("Chargement du scenario de demonstration");

            // ---------------------------------------------------------------- Comptes
            Utilisateur admin = creerUtilisateur(passwordEncoder,
                    "Amel Bouzid", "admin@stb.tn", Role.ADMINISTRATEUR);

            Utilisateur chefApplis = creerUtilisateur(passwordEncoder,
                    "Mokhtar Ben Ali", "chef.applications@stb.tn", Role.CHEF_DE_PROJET);
            Utilisateur chefCanaux = creerUtilisateur(passwordEncoder,
                    "Leila Mansouri", "chef.canaux@stb.tn", Role.CHEF_DE_PROJET);

            Utilisateur devWassim = creerUtilisateur(passwordEncoder,
                    "Wassim Trabelsi", "dev.wassim@stb.tn", Role.DEVELOPPEUR);
            Utilisateur devSarra = creerUtilisateur(passwordEncoder,
                    "Sarra Gharbi", "dev.sarra@stb.tn", Role.DEVELOPPEUR);
            Utilisateur devAnis = creerUtilisateur(passwordEncoder,
                    "Anis Khelifi", "dev.anis@stb.tn", Role.DEVELOPPEUR);

            Utilisateur agenceSfax = creerUtilisateur(passwordEncoder,
                    "Sirine Ben Cheikh", "agence.sfax@stb.tn", Role.DEMANDEUR);
            Utilisateur serviceCredit = creerUtilisateur(passwordEncoder,
                    "Karim Jebali", "service.credit@stb.tn", Role.DEMANDEUR);
            Utilisateur serviceRh = creerUtilisateur(passwordEncoder,
                    "Nadia Sassi", "service.rh@stb.tn", Role.DEMANDEUR);

            // Compte desactive : permet de demontrer le blocage a la connexion (BF 2.1)
            Utilisateur compteInactif = creerUtilisateur(passwordEncoder,
                    "Hedi Rezgui", "ancien.collaborateur@stb.tn", Role.DEMANDEUR);
            compteInactif.setActif(false);

            utilisateurRepository.saveAll(List.of(admin, chefApplis, chefCanaux,
                    devWassim, devSarra, devAnis,
                    agenceSfax, serviceCredit, serviceRh, compteInactif));

            // ---------------------------------------------------------------- Demandes
            List<Demande> creees = new ArrayList<>();

            // --- En attente d'affectation : alimente la tuile « A affecter » du pilotage
            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-001",
                    "Authentification a deux facteurs sur l'espace client",
                    "Le comite securite demande l'ajout d'un second facteur par SMS sur la "
                            + "connexion a l'espace client, conformement aux recommandations BCT.",
                    TypeDemande.DEVELOPPEMENT, Priorite.HAUTE, StatutDemande.NOUVELLE,
                    serviceCredit, null, 2));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-002",
                    "Habilitation consultation pour trois nouveaux conseillers",
                    "Trois conseillers ont rejoint l'agence de Sfax le 1er du mois. Merci de "
                            + "leur ouvrir un acces en consultation sur l'application credit.",
                    TypeDemande.CREATION_ACCES, Priorite.MOYENNE, StatutDemande.NOUVELLE,
                    agenceSfax, null, 4));

            // --- En cours : demandes affectees et demarrees
            Demande bugInterets = creer(demandeRepository, historiqueStatutRepository, "DEM-2026-003",
                    "Erreur de calcul des interets sur les comptes epargne",
                    "Depuis la cloture mensuelle, les interets des comptes epargne sont "
                            + "arrondis au dinar inferieur au lieu du millime. Impact sur "
                            + "l'ensemble des agences.",
                    TypeDemande.CORRECTION_BUG, Priorite.CRITIQUE, StatutDemande.EN_COURS,
                    serviceCredit, devWassim, 6);
            creees.add(bugInterets);

            Demande exportSepa = creer(demandeRepository, historiqueStatutRepository, "DEM-2026-004",
                    "Export SEPA au format XML pour les virements de masse",
                    "Le service comptabilite souhaite exporter les virements de masse au "
                            + "format XML SEPA plutot que par saisie manuelle.",
                    TypeDemande.EVOLUTION, Priorite.HAUTE, StatutDemande.EN_COURS,
                    serviceCredit, devSarra, 11);
            creees.add(exportSepa);

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-005",
                    "Lenteur de l'ecran de consultation des soldes",
                    "L'affichage des soldes prend plus de dix secondes aux heures de pointe, "
                            + "ce qui allonge le temps d'attente au guichet.",
                    TypeDemande.CORRECTION_BUG, Priorite.HAUTE, StatutDemande.EN_COURS,
                    agenceSfax, devAnis, 14));

            // --- En validation : le livrable attend la recette du chef de projet
            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-006",
                    "Tableau de bord des credits en cours pour la direction",
                    "Ecran de synthese presentant l'encours par type de credit et par agence, "
                            + "avec export Excel.",
                    TypeDemande.DEVELOPPEMENT, Priorite.MOYENNE, StatutDemande.EN_VALIDATION,
                    serviceCredit, devSarra, 21));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-007",
                    "Acces au module de reporting pour le service RH",
                    "Ouverture d'un acces en lecture seule au module de reporting pour le "
                            + "suivi des effectifs.",
                    TypeDemande.CREATION_ACCES, Priorite.BASSE, StatutDemande.EN_VALIDATION,
                    serviceRh, devAnis, 25));

            // --- Terminees : alimentent le taux de cloture et le temps moyen de traitement
            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-008",
                    "Migration de la base de production vers PostgreSQL 16",
                    "Montee de version du moteur de base de donnees, avec fenetre de "
                            + "maintenance planifiee un dimanche matin.",
                    TypeDemande.MAINTENANCE, Priorite.HAUTE, StatutDemande.TERMINEE,
                    serviceCredit, devWassim, 45));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-009",
                    "Assistance sur l'export des releves clients",
                    "Le service clientele ne parvenait pas a exporter les releves sur une "
                            + "periode superieure a trois mois : procedure expliquee et documentee.",
                    TypeDemande.ASSISTANCE, Priorite.BASSE, StatutDemande.TERMINEE,
                    agenceSfax, devAnis, 62));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-010",
                    "Purge des journaux applicatifs de plus d'un an",
                    "Les journaux occupaient 80 % du volume disque : purge automatisee mise "
                            + "en place avec retention glissante de douze mois.",
                    TypeDemande.MAINTENANCE, Priorite.MOYENNE, StatutDemande.TERMINEE,
                    serviceRh, devWassim, 88));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-011",
                    "Ajout du numero de compte sur l'avis d'operation",
                    "Le numero de compte est desormais rappele en tete de chaque avis "
                            + "d'operation, a la demande des agences.",
                    TypeDemande.EVOLUTION, Priorite.BASSE, StatutDemande.TERMINEE,
                    agenceSfax, devSarra, 115));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-012",
                    "Correction de l'edition des attestations de solde",
                    "Un caractere accentue tronquait le nom du client sur l'attestation PDF.",
                    TypeDemande.CORRECTION_BUG, Priorite.MOYENNE, StatutDemande.TERMINEE,
                    serviceRh, devAnis, 140));

            // --- Rejetees : montrent qu'une demande peut etre refusee avec tracabilite
            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-013",
                    "Refonte complete de l'ecran de virement",
                    "Refus : le chantier releve du projet de refonte des canaux digitaux "
                            + "prevu au budget de l'exercice suivant, hors perimetre des "
                            + "demandes internes.",
                    TypeDemande.EVOLUTION, Priorite.MOYENNE, StatutDemande.REJETEE,
                    serviceCredit, null, 70));

            creees.add(creer(demandeRepository, historiqueStatutRepository, "DEM-2026-014",
                    "Installation d'un logiciel de retouche photo",
                    "Refus : demande sans rapport avec le systeme d'information bancaire, "
                            + "reorientee vers le service moyens generaux.",
                    TypeDemande.ASSISTANCE, Priorite.BASSE, StatutDemande.REJETEE,
                    serviceRh, null, 95));

            demandeRepository.saveAll(creees);

            // ---------------------------------------------------------------- Discussions
            // Deux fils fournis pour que la messagerie ait du contenu des la premiere ouverture.
            LocalDateTime base = LocalDateTime.now().minusDays(5);
            messageRepository.saveAll(List.of(
                    message(bugInterets, serviceCredit,
                            "Bonjour, l'ecart est constate sur toutes les agences depuis la "
                                    + "cloture du mois. Pouvez-vous confirmer la prise en charge ?",
                            base.plusHours(1), true),
                    message(bugInterets, chefApplis,
                            "Bonjour, demande prise en charge et affectee a Wassim. "
                                    + "Priorite critique compte tenu de l'impact comptable.",
                            base.plusHours(3), true),
                    message(bugInterets, devWassim,
                            "L'arrondi vient d'une conversion en entier dans le calcul mensuel. "
                                    + "Correctif en cours, livraison prevue en recette demain.",
                            base.plusDays(1), false),

                    message(exportSepa, serviceCredit,
                            "Le format attendu est bien pain.001.001.03 ? La comptabilite le "
                                    + "demande pour son interface avec la BCT.",
                            base.plusDays(2), true),
                    message(exportSepa, devSarra,
                            "Oui, pain.001.001.03. Un premier fichier de test vous sera transmis "
                                    + "en fin de semaine pour validation.",
                            base.plusDays(2).plusHours(2), false)));

            log.info("Scenario charge : {} comptes, {} demandes, 5 messages (mot de passe commun : {})",
                    10, creees.size(), MOT_DE_PASSE_DEMO);
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

    private Message message(Demande demande, Utilisateur expediteur, String contenu,
            LocalDateTime dateEnvoi, boolean lu) {

        Message message = new Message();
        message.setDemande(demande);
        message.setExpediteur(expediteur);
        message.setContenu(contenu);
        message.setDateEnvoi(dateEnvoi);
        message.setLu(lu);
        return message;
    }

    /**
     * Crée une demande et l'historique de statut correspondant.
     *
     * <p>L'historique est reconstitué étape par étape plutôt que d'un seul saut : une demande
     * terminée porte donc bien les quatre transitions successives, ce qui rend l'écran
     * d'historique et le calcul du temps moyen de traitement représentatifs.
     */
    private Demande creer(
            DemandeRepository demandeRepository,
            HistoriqueStatutRepository historiqueStatutRepository,
            String numero,
            String titre,
            String description,
            TypeDemande type,
            Priorite priorite,
            StatutDemande statut,
            Utilisateur demandeur,
            Utilisateur responsable,
            int joursDansLePasse) {

        LocalDateTime creation = LocalDateTime.now().minusDays(joursDansLePasse);

        Demande demande = new Demande();
        demande.setNumero(numero);
        demande.setTitre(titre);
        demande.setDescription(description);
        demande.setType(type);
        demande.setPriorite(priorite);
        demande.setStatut(statut);
        demande.setDemandeur(demandeur);
        demande.setResponsable(responsable);
        demande.setDateCreation(creation);
        demande.setDateLimite(creation.toLocalDate().plusDays(30));

        boolean estFinale = statut == StatutDemande.TERMINEE || statut == StatutDemande.REJETEE;
        if (estFinale) {
            demande.setDateCloture(creation.plusDays(Math.max(2, joursDansLePasse / 10)));
        }

        demande = demandeRepository.save(demande);

        Utilisateur auteurTransition = responsable != null ? responsable : demandeur;
        List<HistoriqueStatut> historique = new ArrayList<>();
        historique.add(etape(demande, null, StatutDemande.NOUVELLE, demandeur, creation));

        StatutDemande precedent = StatutDemande.NOUVELLE;
        int jour = 1;
        for (StatutDemande prochain : cheminVers(statut)) {
            historique.add(etape(demande, precedent, prochain, auteurTransition, creation.plusDays(jour)));
            precedent = prochain;
            jour++;
        }

        historiqueStatutRepository.saveAll(historique);
        return demande;
    }

    /** Suite des statuts traversés pour atteindre l'état visé, en respectant le cycle de vie. */
    private List<StatutDemande> cheminVers(StatutDemande cible) {
        return switch (cible) {
            case NOUVELLE -> List.of();
            case EN_COURS -> List.of(StatutDemande.EN_COURS);
            case EN_VALIDATION -> List.of(StatutDemande.EN_COURS, StatutDemande.EN_VALIDATION);
            case TERMINEE -> List.of(StatutDemande.EN_COURS, StatutDemande.EN_VALIDATION,
                    StatutDemande.TERMINEE);
            // Un rejet intervient directement depuis l'etat initial : c'est une decision
            // d'arbitrage, elle ne suppose pas que la demande ait ete travaillee.
            case REJETEE -> List.of(StatutDemande.REJETEE);
        };
    }

    private HistoriqueStatut etape(Demande demande, StatutDemande ancien, StatutDemande nouveau,
            Utilisateur auteur, LocalDateTime date) {

        HistoriqueStatut historique = new HistoriqueStatut();
        historique.setDemande(demande);
        historique.setAncienStatut(ancien);
        historique.setNouveauStatut(nouveau);
        historique.setAuteur(auteur);
        historique.setDateChangement(date);
        return historique;
    }
}
