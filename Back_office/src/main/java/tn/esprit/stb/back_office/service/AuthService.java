package tn.esprit.stb.back_office.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tn.esprit.stb.back_office.dto.AuthResponse;
import tn.esprit.stb.back_office.dto.LoginRequest;
import tn.esprit.stb.back_office.dto.RegisterRequest;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.EmailAlreadyUsedException;
import tn.esprit.stb.back_office.exception.JetonInvalideException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.security.JwtService;
import tn.esprit.stb.back_office.security.UtilisateurDetailsService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UtilisateurDetailsService utilisateurDetailsService;

    public AuthResponse register(RegisterRequest request) {
        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyUsedException("Cet email est déjà utilisé");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(request.getNom());
        utilisateur.setEmail(request.getEmail());
        utilisateur.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
        utilisateur.setRole(request.getRole());

        utilisateur = utilisateurRepository.save(utilisateur);

        UserDetails userDetails = utilisateurDetailsService.loadUserByUsername(utilisateur.getEmail());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token, utilisateur.getId(), utilisateur.getNom(), utilisateur.getEmail(), utilisateur.getRole(), utilisateur.getPhotoUrl());
    }

    /**
     * Génère un jeton de réinitialisation valable 30 minutes (BF 2.1.3).
     * Le jeton est renvoyé pour permettre le test sans service d'e-mail ;
     * en production il serait uniquement transmis par courriel.
     */
    public String demanderReinitialisation(String email) {
        // Email inconnu : on ne lève pas d'erreur, pour ne pas révéler les comptes existants
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email).orElse(null);
        if (utilisateur == null) {
            log.info("Demande de réinitialisation pour un email inconnu : {}", email);
            return null;
        }

        String token = UUID.randomUUID().toString();
        utilisateur.setResetToken(token);
        utilisateur.setResetTokenExpiration(LocalDateTime.now().plusMinutes(30));
        utilisateurRepository.save(utilisateur);

        log.info("Jeton de réinitialisation généré pour {}", email);
        return token;
    }

    public void reinitialiserMotDePasse(String token, String nouveauMotDePasse) {
        Utilisateur utilisateur = utilisateurRepository.findByResetToken(token)
                .orElseThrow(() -> new JetonInvalideException("Jeton de réinitialisation invalide"));

        if (utilisateur.getResetTokenExpiration() == null
                || utilisateur.getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new JetonInvalideException("Ce lien de réinitialisation a expiré");
        }

        utilisateur.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateur.setResetToken(null);
        utilisateur.setResetTokenExpiration(null);
        utilisateurRepository.save(utilisateur);

        log.info("Mot de passe réinitialisé pour {}", utilisateur.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getMotDePasse()));

        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable"));

        UserDetails userDetails = utilisateurDetailsService.loadUserByUsername(utilisateur.getEmail());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token, utilisateur.getId(), utilisateur.getNom(), utilisateur.getEmail(), utilisateur.getRole(), utilisateur.getPhotoUrl());
    }
}
