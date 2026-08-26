package tn.esprit.stb.back_office.service.mail;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.TypeDemande;

/**
 * Déduit un type et une priorité à partir du texte d'un e-mail, par simples mots-clés.
 *
 * <p>Le résultat est une <strong>proposition</strong> affichée à l'écran de qualification,
 * jamais une décision. Le choix de règles explicites plutôt que d'un modèle d'apprentissage
 * est délibéré : elles sont vérifiables, expliquables à un auditeur, et ne nécessitent aucun
 * jeu d'entraînement — trois propriétés qui comptent davantage qu'un gain marginal de
 * précision dans un contexte bancaire.
 *
 * <p>La comparaison ignore la casse et les accents, les e-mails internes étant fréquemment
 * rédigés sans accentuation.
 */
@Service
public class PrequalificationService {

    /**
     * Règles ordonnées de la plus spécifique à la plus générale : le premier type dont un
     * mot-clé apparaît l'emporte. L'ordre d'insertion de {@link LinkedHashMap} porte donc une
     * signification métier et doit être préservé.
     */
    private static final Map<TypeDemande, List<String>> MOTS_CLES_TYPE = new LinkedHashMap<>();

    static {
        MOTS_CLES_TYPE.put(TypeDemande.CREATION_ACCES,
                List.of("acces", "habilitation", "compte utilisateur", "droits", "profil",
                        "mot de passe", "authentification"));
        MOTS_CLES_TYPE.put(TypeDemande.CORRECTION_BUG,
                List.of("bug", "anomalie", "erreur", "ne fonctionne pas", "ne marche pas",
                        "dysfonctionnement", "plantage", "probleme technique"));
        MOTS_CLES_TYPE.put(TypeDemande.EVOLUTION,
                List.of("evolution", "amelioration", "ajouter", "nouvelle fonctionnalite",
                        "souhaite disposer", "serait-il possible"));
        MOTS_CLES_TYPE.put(TypeDemande.MAINTENANCE,
                List.of("maintenance", "mise a jour", "montee de version", "migration",
                        "purge", "sauvegarde"));
        MOTS_CLES_TYPE.put(TypeDemande.DEVELOPPEMENT,
                List.of("developpement", "nouveau module", "nouvelle application",
                        "cahier des charges", "projet"));
    }

    private static final List<String> MOTS_CLES_CRITIQUE =
            List.of("production", "bloquant", "critique", "incident", "arret de service",
                    "impossible de travailler");

    private static final List<String> MOTS_CLES_HAUTE =
            List.of("urgent", "urgence", "au plus vite", "rapidement", "prioritaire", "asap",
                    "des que possible");

    private static final List<String> MOTS_CLES_BASSE =
            List.of("quand vous pourrez", "sans urgence", "pas urgent", "confort",
                    "a votre convenance");

    /**
     * Propose un type de demande.
     *
     * @return le type déduit, ou {@link TypeDemande#ASSISTANCE} par défaut — un message non
     *         classable est presque toujours une question, et c'est la catégorie la moins
     *         engageante pour un traitement automatique
     */
    public TypeDemande deduireType(String sujet, String corps) {
        String texte = normaliser(concatener(sujet, corps));
        for (Map.Entry<TypeDemande, List<String>> regle : MOTS_CLES_TYPE.entrySet()) {
            if (contientUnMotCle(texte, regle.getValue())) {
                return regle.getKey();
            }
        }
        return TypeDemande.ASSISTANCE;
    }

    /**
     * Propose une priorité.
     *
     * <p>{@link Priorite#MOYENNE} par défaut, et jamais {@link Priorite#BASSE} sans indice
     * explicite : sous-estimer une demande urgente coûte plus cher que l'inverse.
     */
    public Priorite deduirePriorite(String sujet, String corps) {
        String texte = normaliser(concatener(sujet, corps));
        if (contientUnMotCle(texte, MOTS_CLES_CRITIQUE)) {
            return Priorite.CRITIQUE;
        }
        // Les tournures minorantes sont testees avant les tournures urgentes, car elles
        // contiennent le mot qu'elles nient : « pas urgent » contient « urgent », et
        // « sans urgence » contient « urgence ». L'ordre inverse classerait en HAUTE des
        // demandes explicitement signalees comme non pressantes.
        if (contientUnMotCle(texte, MOTS_CLES_BASSE)) {
            return Priorite.BASSE;
        }
        if (contientUnMotCle(texte, MOTS_CLES_HAUTE)) {
            return Priorite.HAUTE;
        }
        return Priorite.MOYENNE;
    }

    /** Assemble objet et corps en ignorant les parties absentes. */
    private static String concatener(String sujet, String corps) {
        return (sujet == null ? "" : sujet) + " " + (corps == null ? "" : corps);
    }

    private static boolean contientUnMotCle(String texte, List<String> motsCles) {
        return motsCles.stream().anyMatch(texte::contains);
    }

    /**
     * Passe le texte en minuscules et retire les accents, en décomposant chaque caractère
     * accentué puis en écartant les marques diacritiques.
     */
    private static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        String decompose = Normalizer.normalize(texte.toLowerCase(), Normalizer.Form.NFD);
        StringBuilder sansAccents = new StringBuilder(decompose.length());
        for (char caractere : decompose.toCharArray()) {
            if (Character.getType(caractere) != Character.NON_SPACING_MARK) {
                sansAccents.append(caractere);
            }
        }
        return sansAccents.toString();
    }
}
