package tn.esprit.stb.back_office.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.AdminUpdateUserRequest;
import tn.esprit.stb.back_office.dto.CreateUserRequest;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.dto.UpdateProfileRequest;
import tn.esprit.stb.back_office.dto.UtilisateurDto;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.entities.Utilisateur;
import tn.esprit.stb.back_office.exception.EmailAlreadyUsedException;
import tn.esprit.stb.back_office.exception.UserNotFoundException;
import tn.esprit.stb.back_office.repository.UtilisateurRepository;
import tn.esprit.stb.back_office.repository.UtilisateurSpecifications;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public UtilisateurDto getByEmail(String email) {
        Utilisateur utilisateur = findByEmail(email);
        return toDto(utilisateur);
    }

    public UtilisateurDto updateProfile(String email, UpdateProfileRequest request) {
        Utilisateur utilisateur = findByEmail(email);
        utilisateur.setNom(request.getNom());

        if (request.getNouveauMotDePasse() != null && !request.getNouveauMotDePasse().isBlank()) {
            utilisateur.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        }

        utilisateur = utilisateurRepository.save(utilisateur);
        return toDto(utilisateur);
    }

    public List<UtilisateurDto> listAll() {
        return utilisateurRepository.findAll().stream().map(this::toDto).toList();
    }

    /** Liste paginée et filtrable des utilisateurs (écran d'administration). */
    public PageResponse<UtilisateurDto> rechercher(Role role, Boolean actif, String motCle, int page, int taille) {
        Pageable pageable = PageRequest.of(page, taille, Sort.by(Sort.Direction.ASC, "nom"));
        Page<Utilisateur> resultats = utilisateurRepository.findAll(
                UtilisateurSpecifications.avecCriteres(role, actif, motCle), pageable);

        return PageResponse.de(resultats, resultats.getContent().stream().map(this::toDto).toList());
    }

    /** Utilisateurs pouvant se voir affecter une demande (développeurs et chefs de projet actifs). */
    public List<UtilisateurDto> listAffectables() {
        return utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.DEVELOPPEUR || u.getRole() == Role.CHEF_DE_PROJET)
                .filter(u -> Boolean.TRUE.equals(u.getActif()))
                .map(this::toDto)
                .toList();
    }

    public UtilisateurDto updateRole(Integer id, Role role) {
        Utilisateur utilisateur = findById(id);
        utilisateur.setRole(role);
        utilisateur = utilisateurRepository.save(utilisateur);
        return toDto(utilisateur);
    }

    public UtilisateurDto updatePhoto(String email, String photoUrl) {
        Utilisateur utilisateur = findByEmail(email);
        utilisateur.setPhotoUrl(photoUrl);
        utilisateur = utilisateurRepository.save(utilisateur);
        return toDto(utilisateur);
    }

    public UtilisateurDto create(CreateUserRequest request) {
        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyUsedException("Cet email est déjà utilisé");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(request.getNom());
        utilisateur.setEmail(request.getEmail());
        utilisateur.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
        utilisateur.setRole(request.getRole());
        utilisateur.setActif(true);

        return toDto(utilisateurRepository.save(utilisateur));
    }

    public UtilisateurDto update(Integer id, AdminUpdateUserRequest request) {
        Utilisateur utilisateur = findById(id);

        // L'email doit rester unique s'il change
        if (!utilisateur.getEmail().equals(request.getEmail()) && utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyUsedException("Cet email est déjà utilisé");
        }

        utilisateur.setNom(request.getNom());
        utilisateur.setEmail(request.getEmail());
        utilisateur.setRole(request.getRole());
        utilisateur.setActif(request.getActif());

        if (request.getNouveauMotDePasse() != null && !request.getNouveauMotDePasse().isBlank()) {
            utilisateur.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        }

        return toDto(utilisateurRepository.save(utilisateur));
    }

    public UtilisateurDto toggleStatus(Integer id) {
        Utilisateur utilisateur = findById(id);
        utilisateur.setActif(!Boolean.TRUE.equals(utilisateur.getActif()));
        return toDto(utilisateurRepository.save(utilisateur));
    }

    public void delete(Integer id) {
        if (!utilisateurRepository.existsById(id)) {
            throw new UserNotFoundException("Utilisateur introuvable");
        }
        utilisateurRepository.deleteById(id);
    }

    private Utilisateur findById(Integer id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable"));
    }

    private Utilisateur findByEmail(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable"));
    }

    private UtilisateurDto toDto(Utilisateur utilisateur) {
        return new UtilisateurDto(
                utilisateur.getId(),
                utilisateur.getNom(),
                utilisateur.getEmail(),
                utilisateur.getRole(),
                utilisateur.getPhotoUrl(),
                utilisateur.getActif(),
                utilisateur.getDateCreation());
    }
}
