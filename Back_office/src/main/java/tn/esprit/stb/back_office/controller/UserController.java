package tn.esprit.stb.back_office.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.UpdateProfileRequest;
import tn.esprit.stb.back_office.dto.UtilisateurDto;
import tn.esprit.stb.back_office.service.FileStorageService;
import tn.esprit.stb.back_office.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    @GetMapping("/me")
    public ResponseEntity<UtilisateurDto> me(Principal principal) {
        return ResponseEntity.ok(userService.getByEmail(principal.getName()));
    }

    /** Utilisateurs affectables à une demande — utilisé par le chef de projet. */
    @GetMapping("/affectables")
    @PreAuthorize("hasAnyRole('CHEF_DE_PROJET', 'ADMINISTRATEUR')")
    public ResponseEntity<List<UtilisateurDto>> affectables() {
        return ResponseEntity.ok(userService.listAffectables());
    }

    @PutMapping("/me")
    public ResponseEntity<UtilisateurDto> updateMe(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.getName(), request));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UtilisateurDto> updatePhoto(Principal principal, @RequestParam("file") MultipartFile file) {
        String photoUrl = fileStorageService.storeAvatar(file);
        return ResponseEntity.ok(userService.updatePhoto(principal.getName(), photoUrl));
    }
}
