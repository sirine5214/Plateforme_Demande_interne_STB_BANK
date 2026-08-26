package tn.esprit.stb.back_office.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * E-mail reçu sur la boîte partagée de la Direction Développement Digital, stocké tel quel
 * en attendant sa qualification.
 *
 * <p>Cette entité est la trace brute et immuable de ce qui est arrivé. La demande qui en
 * découle est un enregistrement distinct, relié par {@link #demande} : on peut ainsi toujours
 * remonter d'une demande vers le message d'origine, ce que la gestion actuelle par boîte
 * Outlook et fichiers Excel ne permet pas.
 */
@Entity
@Table(name = "email_entrant", indexes = {
        @Index(name = "idx_email_entrant_statut", columnList = "statut, date_reception")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailEntrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * En-tête « Message-ID » du courriel, garanti unique par le serveur d'envoi.
     *
     * <p>C'est la clé de l'idempotence : la contrainte d'unicité empêche le même message
     * d'être importé deux fois quand la relève repasse sur la boîte ou que l'application
     * redémarre en cours de traitement.
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    private String messageId;

    @Column(name = "expediteur_email", nullable = false, length = 320)
    private String expediteurEmail;

    @Column(name = "expediteur_nom", length = 255)
    private String expediteurNom;

    @Column(nullable = false, length = 500)
    private String sujet;

    /**
     * Corps du message, en texte brut.
     *
     * <p>Seule forme conservée : le HTML d'origine n'est jamais stocké, afin qu'aucun
     * balisage provenant d'un expéditeur non authentifié ne puisse atteindre le
     * navigateur d'un administrateur.
     */
    @Column(name = "corps_texte", length = 20000)
    private String corpsTexte;

    /** Date d'émission annoncée par le serveur d'envoi. */
    @Column(name = "date_reception", nullable = false)
    private LocalDateTime dateReception;

    /** Date à laquelle la plateforme a effectivement importé le message. */
    @Column(name = "date_ingestion", nullable = false)
    private LocalDateTime dateIngestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEmail statut = StatutEmail.NON_TRAITE;

    /**
     * Type déduit des mots-clés du message, à titre de proposition uniquement.
     *
     * <p>Le chef de projet le confirme ou le corrige au moment de la qualification ; il n'est
     * jamais repris tel quel dans la demande sans validation humaine.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_propose", length = 20)
    private TypeDemande typePropose;

    /** Priorité déduite des mots-clés, également soumise à validation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "priorite_proposee", length = 20)
    private Priorite prioriteProposee;

    /** Demande issue de la qualification, nulle tant que l'e-mail n'est pas converti. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_demande")
    private Demande demande;

    /** Agent ayant qualifié le message, nul tant qu'il est en attente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_traite_par")
    private Utilisateur traitePar;

    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    /** Justification obligatoire lorsqu'un message est écarté, pour l'audit. */
    @Column(name = "motif_ignore", length = 500)
    private String motifIgnore;

    @PrePersist
    void onCreate() {
        if (dateIngestion == null) {
            dateIngestion = LocalDateTime.now();
        }
        if (dateReception == null) {
            dateReception = dateIngestion;
        }
    }
}
