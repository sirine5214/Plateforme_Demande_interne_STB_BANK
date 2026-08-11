package tn.esprit.stb.back_office.entities;

/** Nature de la notification : elle détermine l'icône et la destination au clic. */
public enum TypeNotification {
    /** Événement du cycle de vie d'une demande (création, affectation, statut, priorité). */
    DEMANDE,
    /** Nouveau message dans le fil de discussion d'une demande. */
    MESSAGE
}
