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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(nullable = false)
    private Boolean lu = false;

    // Pas de columnDefinition avec « default » : Hibernate le réinjecte tel quel dans
    // ALTER COLUMN ... SET DATA TYPE, que PostgreSQL refuse.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeNotification type = TypeNotification.DEMANDE;

    /** Destinataire de la notification. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_destinataire", nullable = false)
    private Utilisateur destinataire;

    /** Demande concernée (permet au front de rebondir dessus). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_demande")
    private Demande demande;

    /** Utilisateur à l'origine de l'événement notifié. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_auteur")
    private Utilisateur auteur;

    @PrePersist
    void onCreate() {
        if (dateEnvoi == null) {
            dateEnvoi = LocalDateTime.now();
        }
        if (lu == null) {
            lu = false;
        }
    }
}
