package tn.esprit.stb.back_office.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.NotificationDto;
import tn.esprit.stb.back_office.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> lister(Principal principal) {
        return ResponseEntity.ok(notificationService.lister(principal.getName()));
    }

    @GetMapping("/non-lues/compte")
    public ResponseEntity<Map<String, Long>> compterNonLues(Principal principal) {
        return ResponseEntity.ok(Map.of("nonLues", notificationService.compterNonLues(principal.getName())));
    }

    @PutMapping("/{id}/lue")
    public ResponseEntity<NotificationDto> marquerCommeLue(Principal principal, @PathVariable Integer id) {
        return ResponseEntity.ok(notificationService.marquerCommeLue(principal.getName(), id));
    }

    @PutMapping("/tout-lire")
    public ResponseEntity<Void> toutMarquerCommeLu(Principal principal) {
        notificationService.toutMarquerCommeLu(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
