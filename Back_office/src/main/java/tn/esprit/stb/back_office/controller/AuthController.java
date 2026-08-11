package tn.esprit.stb.back_office.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.AuthResponse;
import tn.esprit.stb.back_office.dto.ForgotPasswordRequest;
import tn.esprit.stb.back_office.dto.LoginRequest;
import tn.esprit.stb.back_office.dto.RegisterRequest;
import tn.esprit.stb.back_office.dto.ResetPasswordRequest;
import tn.esprit.stb.back_office.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.exposer-jeton-reset:false}")
    private boolean exposerJetonReset;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Demande de réinitialisation.
     * Le jeton n'est renvoyé au client que si {@code app.exposer-jeton-reset} est activé
     * (pratique en développement, à laisser désactivé en production où il part par e-mail).
     * La réponse est volontairement identique que le compte existe ou non, pour ne pas
     * révéler quelles adresses sont enregistrées.
     */
    @PostMapping("/mot-de-passe-oublie")
    public ResponseEntity<Map<String, String>> motDePasseOublie(@Valid @RequestBody ForgotPasswordRequest request) {
        String token = authService.demanderReinitialisation(request.getEmail());

        Map<String, String> reponse = new LinkedHashMap<>();
        reponse.put("message", "Si un compte correspond à cette adresse, un lien de réinitialisation a été envoyé");
        if (exposerJetonReset && token != null) {
            reponse.put("token", token);
        }
        return ResponseEntity.ok(reponse);
    }

    @PostMapping("/reinitialiser-mot-de-passe")
    public ResponseEntity<Map<String, String>> reinitialiser(@Valid @RequestBody ResetPasswordRequest request) {
        authService.reinitialiserMotDePasse(request.getToken(), request.getNouveauMotDePasse());
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
    }
}
