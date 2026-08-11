package tn.esprit.stb.back_office.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Diffuse les événements temps réel (notifications, messages) aux navigateurs connectés.
 * Un même utilisateur peut avoir plusieurs onglets ouverts : on conserve donc un ensemble
 * de sessions par adresse e-mail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TempsReelHandler extends TextWebSocketHandler {

    public static final String ATTRIBUT_EMAIL = "email";

    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> sessionsParUtilisateur = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String email = (String) session.getAttributes().get(ATTRIBUT_EMAIL);
        if (email == null) {
            fermerSilencieusement(session);
            return;
        }

        sessionsParUtilisateur
                .computeIfAbsent(email, cle -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.debug("WebSocket connecté pour {}", email);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String email = (String) session.getAttributes().get(ATTRIBUT_EMAIL);
        if (email == null) {
            return;
        }

        Set<WebSocketSession> sessions = sessionsParUtilisateur.get(email);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsParUtilisateur.remove(email);
            }
        }
    }

    /**
     * Envoie un événement à toutes les sessions ouvertes d'un utilisateur.
     * Un échec d'envoi ne doit jamais interrompre le traitement métier appelant.
     */
    public void envoyer(String email, String type, Object charge) {
        Set<WebSocketSession> sessions = sessionsParUtilisateur.get(email);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String contenu;
        try {
            // Jackson 3 lève des exceptions non contrôlées (JacksonException)
            contenu = objectMapper.writeValueAsString(new EvenementTempsReel(type, charge));
        } catch (JacksonException e) {
            log.warn("Sérialisation de l'événement « {} » impossible : {}", type, e.getMessage());
            return;
        }

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(contenu));
                }
            } catch (IOException e) {
                log.debug("Envoi WebSocket échoué pour {} : {}", email, e.getMessage());
            }
        }
    }

    private void fermerSilencieusement(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (IOException e) {
            log.debug("Fermeture de session échouée : {}", e.getMessage());
        }
    }

    /** Enveloppe des événements poussés au navigateur. */
    public record EvenementTempsReel(String type, Object charge) {
    }
}
