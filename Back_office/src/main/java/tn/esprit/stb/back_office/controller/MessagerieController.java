package tn.esprit.stb.back_office.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.EnvoyerMessageRequest;
import tn.esprit.stb.back_office.dto.MessageDto;
import tn.esprit.stb.back_office.service.MessagerieService;

@RestController
@RequestMapping("/api/demandes/{demandeId}/messages")
@RequiredArgsConstructor
public class MessagerieController {

    private final MessagerieService messagerieService;

    @GetMapping
    public ResponseEntity<List<MessageDto>> lister(Principal principal, @PathVariable Integer demandeId) {
        return ResponseEntity.ok(messagerieService.lister(principal.getName(), demandeId));
    }

    @PostMapping
    public ResponseEntity<MessageDto> envoyer(
            Principal principal, @PathVariable Integer demandeId, @Valid @RequestBody EnvoyerMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagerieService.envoyer(principal.getName(), demandeId, request.getContenu()));
    }

    @PutMapping("/lus")
    public ResponseEntity<Void> marquerLus(Principal principal, @PathVariable Integer demandeId) {
        messagerieService.marquerLus(principal.getName(), demandeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/non-lus")
    public ResponseEntity<Map<String, Long>> compterNonLus(Principal principal, @PathVariable Integer demandeId) {
        return ResponseEntity.ok(Map.of("nonLus", messagerieService.compterNonLus(principal.getName(), demandeId)));
    }
}
