package tn.esprit.stb.back_office.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.ConversionEmailRequest;
import tn.esprit.stb.back_office.dto.DemandeDto;
import tn.esprit.stb.back_office.dto.EmailEntrantDto;
import tn.esprit.stb.back_office.dto.IgnorerEmailRequest;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.entities.StatutEmail;
import tn.esprit.stb.back_office.service.mail.EmailIngestionService;
import tn.esprit.stb.back_office.service.mail.EmailTriageService;

/**
 * Boîte de réception partagée de la Direction Développement Digital.
 *
 * <p>L'ensemble du contrôleur est réservé aux administrateurs et aux chefs de projet : ce
 * sont les seuls habilités à qualifier une demande arrivée par courriel. La restriction est
 * portée au niveau de la classe pour qu'aucune méthode ajoutée par la suite ne puisse être
 * exposée par simple oubli.
 */
@RestController
@RequestMapping("/api/emails")
@PreAuthorize("hasAnyRole('CHEF_DE_PROJET', 'ADMINISTRATEUR')")
@RequiredArgsConstructor
public class EmailEntrantController {

    private final EmailTriageService emailTriageService;
    private final EmailIngestionService emailIngestionService;

    @GetMapping
    public ResponseEntity<PageResponse<EmailEntrantDto>> lister(
            @RequestParam(defaultValue = "NON_TRAITE") StatutEmail statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {

        return ResponseEntity.ok(emailTriageService.lister(statut, page, taille));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailEntrantDto> consulter(@PathVariable Integer id) {
        return ResponseEntity.ok(emailTriageService.consulter(id));
    }

    /** Compteur affiché en pastille dans le menu du back-office. */
    @GetMapping("/non-traites/compte")
    public ResponseEntity<Map<String, Long>> compterNonTraites() {
        return ResponseEntity.ok(Map.of("nonTraites", emailTriageService.compterNonTraites()));
    }

    @PostMapping("/{id}/convertir")
    public ResponseEntity<DemandeDto> convertir(Principal principal, @PathVariable Integer id,
            @Valid @RequestBody ConversionEmailRequest requete) {

        return ResponseEntity.ok(emailTriageService.convertir(principal.getName(), id, requete));
    }

    @PostMapping("/{id}/ignorer")
    public ResponseEntity<EmailEntrantDto> ignorer(Principal principal, @PathVariable Integer id,
            @Valid @RequestBody IgnorerEmailRequest requete) {

        return ResponseEntity.ok(
                emailTriageService.ignorer(principal.getName(), id, requete.getMotif()));
    }

    /**
     * Déclenche une relève immédiate, sans attendre le prochain passage du planificateur.
     *
     * <p>Utile en démonstration et lorsqu'un utilisateur signale un e-mail qui n'apparaît pas
     * encore. Réservé aux administrateurs : une relève est une opération coûteuse côté
     * serveur de messagerie, et ne doit pas pouvoir être déclenchée en boucle.
     */
    @PostMapping("/relever")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Map<String, Integer>> relever() {
        return ResponseEntity.ok(Map.of("importes", emailIngestionService.releverEtImporter()));
    }
}
