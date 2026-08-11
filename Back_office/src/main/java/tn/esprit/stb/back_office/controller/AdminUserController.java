package tn.esprit.stb.back_office.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.AdminUpdateUserRequest;
import tn.esprit.stb.back_office.dto.CreateUserRequest;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.dto.UpdateRoleRequest;
import tn.esprit.stb.back_office.dto.UtilisateurDto;
import tn.esprit.stb.back_office.entities.Role;
import tn.esprit.stb.back_office.service.UserService;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UtilisateurDto>> listUsers() {
        return ResponseEntity.ok(userService.listAll());
    }

    /** Liste paginée et filtrable (rôle, état, mot-clé). */
    @GetMapping("/recherche")
    public ResponseEntity<PageResponse<UtilisateurDto>> rechercher(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) String motCle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int taille) {
        return ResponseEntity.ok(userService.rechercher(role, actif, motCle, page, taille));
    }

    @PostMapping
    public ResponseEntity<UtilisateurDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UtilisateurDto> updateUser(@PathVariable Integer id, @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UtilisateurDto> updateRole(@PathVariable Integer id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(userService.updateRole(id, request.getRole()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<UtilisateurDto> toggleStatus(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
