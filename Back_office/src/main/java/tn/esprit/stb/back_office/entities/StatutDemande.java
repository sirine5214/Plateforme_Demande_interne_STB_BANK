package tn.esprit.stb.back_office.entities;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une demande :
 * Nouvelle → En cours → En validation → Terminée (ou Rejetée à tout moment avant clôture).
 * Les transitions autorisées sont portées par l'énumération pour éviter les sauts d'étape.
 */
public enum StatutDemande {

    NOUVELLE,
    EN_COURS,
    EN_VALIDATION,
    TERMINEE,
    REJETEE;

    /** Statuts atteignables depuis le statut courant. Un statut final ne mène nulle part. */
    public Set<StatutDemande> transitionsAutorisees() {
        return switch (this) {
            case NOUVELLE -> EnumSet.of(EN_COURS, REJETEE);
            // Un retour en arrière reste possible tant que la demande n'est pas clôturée
            case EN_COURS -> EnumSet.of(EN_VALIDATION, NOUVELLE, REJETEE);
            // La validation peut renvoyer la demande au développeur pour correction
            case EN_VALIDATION -> EnumSet.of(TERMINEE, EN_COURS, REJETEE);
            case TERMINEE, REJETEE -> Collections.emptySet();
        };
    }

    public boolean peutAllerVers(StatutDemande cible) {
        return transitionsAutorisees().contains(cible);
    }

    /** Une demande clôturée (terminée ou rejetée) est figée. */
    public boolean estFinal() {
        return this == TERMINEE || this == REJETEE;
    }
}
