import { TypeDemande } from 'src/app/theme/shared/service/demande.service';

/**
 * Chaque type de demande n'appelle pas le même travail : ce référentiel pilote
 * l'aide affichée, le canevas de description et les pièces justificatives attendues.
 */
export interface GuideType {
  /** Consigne courte affichée en tête du formulaire. */
  aide: string;
  /** Canevas pré-rempli dans la description à la création. */
  canevas: string;
  /** Pièces jointes attendues pour ce type de demande. */
  piecesAttendues: string[];
  /** Une pièce jointe est-elle indispensable pour traiter la demande ? */
  pieceObligatoire: boolean;
}

export const GUIDES_TYPE: Record<TypeDemande, GuideType> = {
  DEVELOPPEMENT: {
    aide: "Décrivez le besoin fonctionnel attendu et le résultat visé, pas la solution technique.",
    canevas: 'Besoin fonctionnel :\nUtilisateurs concernés :\nRésultat attendu :\nContraintes éventuelles :',
    piecesAttendues: ['Spécification ou maquette', 'Exemple de données si pertinent'],
    pieceObligatoire: false
  },
  CORRECTION_BUG: {
    aide: "Indiquez comment reproduire l'anomalie : sans étapes précises, le développeur ne pourra pas la corriger.",
    canevas:
      "Application concernée :\nÉtapes pour reproduire :\n1.\n2.\nRésultat observé :\nRésultat attendu :\nDate et heure de l'incident :",
    piecesAttendues: ["Capture d'écran de l'erreur", 'Extrait de log'],
    pieceObligatoire: true
  },
  MAINTENANCE: {
    aide: "Précisez l'application, la fenêtre d'intervention souhaitée et l'impact sur les utilisateurs.",
    canevas: "Application concernée :\nNature de l'intervention :\nFenêtre souhaitée :\nImpact utilisateurs :",
    piecesAttendues: ["Procédure ou plan d'intervention"],
    pieceObligatoire: false
  },
  ASSISTANCE: {
    aide: 'Décrivez le blocage rencontré et ce que vous avez déjà tenté.',
    canevas: 'Contexte :\nBlocage rencontré :\nActions déjà tentées :\nUrgence :',
    piecesAttendues: ["Capture d'écran du problème"],
    pieceObligatoire: false
  },
  CREATION_ACCES: {
    aide: "Une demande d'accès doit être justifiée et validée : indiquez l'application, le profil et le motif.",
    canevas:
      "Application ou système :\nProfil / niveau d'accès demandé :\nMotif de la demande :\nDurée souhaitée :\nResponsable hiérarchique :",
    piecesAttendues: ['Validation du responsable hiérarchique'],
    pieceObligatoire: true
  },
  EVOLUTION: {
    aide: "Expliquez la limite de l'existant et l'amélioration attendue.",
    canevas: 'Application concernée :\nFonctionnement actuel :\nÉvolution souhaitée :\nGain attendu :',
    piecesAttendues: ['Maquette ou exemple', 'Spécification détaillée'],
    pieceObligatoire: false
  }
};
