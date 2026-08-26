package tn.esprit.stb.back_office.service.mail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Boîte aux lettres en mémoire, sans serveur de messagerie.
 *
 * <p>Active par défaut tant que {@code stb.mail.client} n'est pas positionné, ce qui permet
 * de faire tourner l'application et sa chaîne d'intégration continue sans dépendre d'un
 * compte de messagerie — condition nécessaire pour que les tests restent reproductibles.
 * Basculer sur {@code stb.mail.client=imap} activera l'implémentation IMAP.
 *
 * <p>Le comportement reproduit la sémantique IMAP : un message relevé reste présent jusqu'à
 * ce que {@link #marquerCommeTraite(String)} le retire du lot des non-lus.
 */
@Component
@ConditionalOnProperty(prefix = "stb.mail", name = "client", havingValue = "fake",
        matchIfMissing = true)
public class FakeMailboxClient implements MailboxClient {

    private final List<EmailBrut> messages = new CopyOnWriteArrayList<>();
    private final Set<String> traites = ConcurrentHashMap.newKeySet();

    public FakeMailboxClient() {
        messages.addAll(jeuDeDemonstration());
    }

    @Override
    public List<EmailBrut> recupererNouveauxMessages(int limite) {
        if (limite <= 0) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> !traites.contains(message.messageId()))
                .sorted(Comparator.comparing(EmailBrut::dateReception))
                .limit(limite)
                .toList();
    }

    @Override
    public void marquerCommeTraite(String messageId) {
        traites.add(messageId);
    }

    /** Dépose un message dans la boîte : utilisé par les tests pour construire un scénario. */
    public void deposer(EmailBrut message) {
        messages.add(message);
    }

    /** Vide entièrement la boîte, y compris les messages de démonstration. */
    public void vider() {
        messages.clear();
        traites.clear();
    }

    /**
     * Trois messages représentatifs des demandes réellement reçues par la direction : une
     * création d'accès, une anomalie de production et une évolution applicative.
     *
     * <p>Le premier provient d'une adresse présente dans l'annuaire, les deux autres non :
     * l'écran de qualification montre ainsi les deux cas — rattachement suggéré et
     * expéditeur inconnu — sans qu'il faille préparer quoi que ce soit.
     */
    private static List<EmailBrut> jeuDeDemonstration() {
        LocalDateTime maintenant = LocalDateTime.now();
        List<EmailBrut> jeu = new ArrayList<>();

        jeu.add(new EmailBrut(
                "demo-acces-001@stb.com.tn",
                "agence.sfax@stb.tn",
                "Sirine Ben Cheikh",
                "Demande de creation d'acces a l'application de gestion des credits",
                "Bonjour, un nouveau conseiller rejoint l'agence lundi. "
                        + "Merci de lui creer un compte avec les habilitations de consultation.",
                null,
                maintenant.minusHours(6),
                List.of()));

        jeu.add(new EmailBrut(
                "demo-anomalie-002@stb.com.tn",
                "sonia.brahmi@stb.com.tn",  // hors annuaire : aucun compte ne correspond
                "Sonia Brahmi",
                "URGENT - erreur bloquante en production sur l'edition des releves",
                "Bonjour, depuis ce matin l'edition des releves renvoie une erreur. "
                        + "Le service est bloquant pour les agences, merci de traiter en urgence.",
                null,
                maintenant.minusHours(2),
                List.of()));

        jeu.add(new EmailBrut(
                "demo-evolution-003@stb.com.tn",
                "karim.jendoubi@stb.com.tn",  // hors annuaire egalement
                "Karim Jendoubi",
                "Evolution souhaitee sur le module de reporting",
                "Bonjour, serait-il possible d'ajouter un export Excel des statistiques "
                        + "mensuelles ? Ce n'est pas urgent, c'est un confort d'utilisation.",
                null,
                maintenant.minusMinutes(30),
                List.of()));

        return jeu;
    }
}
