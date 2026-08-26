package tn.esprit.stb.back_office.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Fichier reçu en pièce jointe d'un e-mail, distinct de {@link PieceJointe}.
 *
 * <p>La séparation est volontaire. Une pièce jointe de demande a été déposée par un
 * utilisateur authentifié depuis l'application ; celle-ci arrive d'un expéditeur dont
 * l'identité n'est pas vérifiée. Les deux n'ont ni le même niveau de confiance, ni le même
 * cycle de vie — celle-ci existe avant même qu'une demande ne soit créée, et peut ne jamais
 * en rejoindre une si le message est écarté.
 *
 * <p>Le {@link #nomFichierOrigine} est conservé pour l'affichage uniquement : le nom réel sur
 * disque est un identifiant aléatoire, afin qu'un nom malveillant ne puisse pas influencer le
 * chemin de stockage.
 */
@Entity
@Table(name = "piece_jointe_email")
@Getter
@Setter
@NoArgsConstructor
public class PieceJointeEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Nom annoncé par l'expéditeur, affiché tel quel mais jamais utilisé comme chemin. */
    @Column(name = "nom_fichier_origine", nullable = false, length = 255)
    private String nomFichierOrigine;

    /** URL publique servie par l'application, pointant vers un nom de fichier aléatoire. */
    @Column(name = "chemin_fichier", nullable = false, length = 500)
    private String cheminFichier;

    /** Type MIME déclaré par l'expéditeur : indicatif, jamais utilisé comme contrôle. */
    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "date_ajout", nullable = false)
    private LocalDateTime dateAjout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_email_entrant", nullable = false)
    private EmailEntrant emailEntrant;

    @PrePersist
    void onCreate() {
        if (dateAjout == null) {
            dateAjout = LocalDateTime.now();
        }
    }
}
