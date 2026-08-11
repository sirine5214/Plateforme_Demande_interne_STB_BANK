package tn.esprit.stb.back_office.websocket;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.security.JwtService;

/**
 * Authentifie la connexion WebSocket. Le navigateur ne pouvant pas poser d'en-tête
 * « Authorization » lors du handshake, le jeton est transmis en paramètre d'URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            log.debug("Handshake WebSocket refusé : jeton absent");
            return refuser(response);
        }

        try {
            attributes.put(TempsReelHandler.ATTRIBUT_EMAIL, jwtService.extractUsername(token));
            return true;
        } catch (JwtException e) {
            log.debug("Handshake WebSocket refusé : jeton invalide ({})", e.getMessage());
            return refuser(response);
        }
    }

    /**
     * Sans statut explicite, un handshake interrompu renvoie un 200 vide,
     * indiscernable d'un succès côté client. On répond donc 401.
     */
    private boolean refuser(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {
        // Rien à faire après le handshake
    }
}
