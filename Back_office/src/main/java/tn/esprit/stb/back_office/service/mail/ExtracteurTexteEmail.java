package tn.esprit.stb.back_office.service.mail;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Réduit le corps d'un e-mail à du texte brut lisible.
 *
 * <p>La plateforme ne conserve <strong>jamais</strong> le HTML d'un message entrant. Ce choix
 * est plus sûr que de l'assainir : assainir suppose de maintenir une liste blanche correcte
 * face à un contenu écrit par un expéditeur non authentifié, alors que ne rien conserver
 * supprime purement et simplement la surface d'attaque. Le front-office n'affiche que du
 * texte, qu'Angular échappe d'office.
 *
 * <p>L'extraction est volontairement approximative : son but est de rendre la demande
 * lisible pour le chef de projet qui la qualifie, pas de restituer fidèlement la mise en
 * forme. Le message d'origine reste de toute façon disponible dans la boîte aux lettres.
 */
@Component
public class ExtracteurTexteEmail {

    /**
     * Les blocs script et style sont retirés avec leur contenu : sans cela, le code
     * JavaScript ou les règles CSS se retrouveraient dans le texte affiché.
     */
    private static final Pattern SCRIPTS = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern STYLES = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern COMMENTAIRES = Pattern.compile("(?s)<!--.*?-->");

    /** Balises dont la fermeture marque une rupture de ligne dans le rendu d'origine. */
    private static final Pattern SAUTS_DE_LIGNE =
            Pattern.compile("(?i)<(br|/p|/div|/tr|/li|/h[1-6])[^>]*>");

    private static final Pattern BALISES = Pattern.compile("<[^>]*>");

    /** Espaces horizontaux répétés, sans toucher aux retours à la ligne déjà posés. */
    private static final Pattern ESPACES_MULTIPLES = Pattern.compile("[ \\t\\x0B\\f]+");

    /** Trois lignes vides ou plus deviennent une seule ligne vide. */
    private static final Pattern LIGNES_VIDES = Pattern.compile("(\\r?\\n){3,}");

    /**
     * Extrait le texte lisible d'un corps HTML.
     *
     * @return le texte extrait, ou {@code null} si l'entrée est absente ou vide
     */
    public String versTexte(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        String texte = SCRIPTS.matcher(html).replaceAll("");
        texte = STYLES.matcher(texte).replaceAll("");
        texte = COMMENTAIRES.matcher(texte).replaceAll("");
        texte = SAUTS_DE_LIGNE.matcher(texte).replaceAll("\n");
        texte = BALISES.matcher(texte).replaceAll("");
        texte = decoderEntites(texte);
        texte = ESPACES_MULTIPLES.matcher(texte).replaceAll(" ");
        texte = LIGNES_VIDES.matcher(texte).replaceAll("\n\n");

        String resultat = texte.strip();
        return resultat.isEmpty() ? null : resultat;
    }

    /**
     * Décode les entités HTML les plus courantes.
     *
     * <p>{@code &amp;} est traité en dernier : le décoder en premier transformerait
     * {@code &amp;lt;} en {@code <}, ce qui reconstituerait une balise que l'expéditeur avait
     * précisément échappée.
     */
    private static String decoderEntites(String texte) {
        return texte
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&eacute;", "é")
                .replace("&egrave;", "è")
                .replace("&agrave;", "à")
                .replace("&ccedil;", "ç")
                .replace("&amp;", "&");
    }
}
