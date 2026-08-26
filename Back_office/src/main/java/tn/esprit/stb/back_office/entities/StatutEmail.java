package tn.esprit.stb.back_office.entities;

/**
 * Étape de traitement d'un e-mail arrivé dans la boîte partagée de la direction.
 *
 * <p>Un e-mail n'est jamais transformé automatiquement en {@link Demande} : son expéditeur
 * n'est pas authentifié et l'en-tête « From » est falsifiable. Il reste donc en
 * {@link #NON_TRAITE} jusqu'à ce qu'un administrateur ou un chef de projet le qualifie.
 */
public enum StatutEmail {

    /** Reçu, en attente de qualification humaine. */
    NON_TRAITE,

    /** Qualifié et transformé en demande : le lien est conservé pour la traçabilité. */
    CONVERTI,

    /** Écarté volontairement (spam, hors périmètre, doublon) : conservé, jamais supprimé. */
    IGNORE
}
