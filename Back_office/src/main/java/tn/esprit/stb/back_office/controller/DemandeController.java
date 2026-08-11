package tn.esprit.stb.back_office.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.esprit.stb.back_office.dto.AffectationRequest;
import tn.esprit.stb.back_office.dto.CreateDemandeRequest;
import tn.esprit.stb.back_office.dto.DemandeDto;
import tn.esprit.stb.back_office.dto.HistoriqueStatutDto;
import tn.esprit.stb.back_office.dto.PageResponse;
import tn.esprit.stb.back_office.dto.PieceJointeDto;
import tn.esprit.stb.back_office.dto.StatistiquesDto;
import tn.esprit.stb.back_office.dto.UpdateDemandeRequest;
import tn.esprit.stb.back_office.dto.UpdateStatutRequest;
import tn.esprit.stb.back_office.entities.Priorite;
import tn.esprit.stb.back_office.entities.StatutDemande;
import tn.esprit.stb.back_office.entities.TypeDemande;
import tn.esprit.stb.back_office.service.DemandeService;

@RestController
@RequestMapping("/api/demandes")
@RequiredArgsConstructor
public class DemandeController {

    private final DemandeService demandeService;

    /** Liste filtrée automatiquement selon le rôle de l'utilisateur connecté. */
    @GetMapping
    public ResponseEntity<List<DemandeDto>> lister(Principal principal) {
        return ResponseEntity.ok(demandeService.listerPour(principal.getName()));
    }

    /** Recherche multicritère paginée (BF 2.6) — tous les paramètres sont optionnels. */
    @GetMapping("/recherche")
    public ResponseEntity<PageResponse<DemandeDto>> rechercher(
            Principal principal,
            @RequestParam(required = false) StatutDemande statut,
            @RequestParam(required = false) Priorite priorite,
            @RequestParam(required = false) TypeDemande type,
            @RequestParam(required = false) Integer responsableId,
            @RequestParam(required = false) String motCle,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int taille) {

        return ResponseEntity.ok(demandeService.rechercher(
                principal.getName(), statut, priorite, type, responsableId, motCle, dateDebut, dateFin, page, taille));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DEMANDEUR', 'CHEF_DE_PROJET', 'ADMINISTRATEUR')")
    public ResponseEntity<DemandeDto> creer(Principal principal, @Valid @RequestBody CreateDemandeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demandeService.creer(principal.getName(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemandeDto> consulter(Principal principal, @PathVariable Integer id) {
        return ResponseEntity.ok(demandeService.consulter(principal.getName(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemandeDto> modifier(
            Principal principal, @PathVariable Integer id, @Valid @RequestBody UpdateDemandeRequest request) {
        return ResponseEntity.ok(demandeService.modifier(principal.getName(), id, request));
    }

    @GetMapping("/{id}/historique")
    public ResponseEntity<List<HistoriqueStatutDto>> historique(Principal principal, @PathVariable Integer id) {
        return ResponseEntity.ok(demandeService.historique(principal.getName(), id));
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<DemandeDto> changerStatut(
            Principal principal, @PathVariable Integer id, @Valid @RequestBody UpdateStatutRequest request) {
        return ResponseEntity.ok(demandeService.changerStatut(principal.getName(), id, request.getStatut()));
    }

    @PutMapping("/{id}/responsable")
    @PreAuthorize("hasAnyRole('CHEF_DE_PROJET', 'ADMINISTRATEUR')")
    public ResponseEntity<DemandeDto> affecter(
            Principal principal, @PathVariable Integer id, @Valid @RequestBody AffectationRequest request) {
        return ResponseEntity.ok(demandeService.affecter(principal.getName(), id, request.getResponsableId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(Principal principal, @PathVariable Integer id) {
        demandeService.supprimer(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Pièces jointes (BF 2.2.6) ----------

    @PostMapping(value = "/{id}/pieces-jointes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PieceJointeDto> ajouterPieceJointe(
            Principal principal, @PathVariable Integer id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(demandeService.ajouterPieceJointe(principal.getName(), id, file));
    }

    @GetMapping("/{id}/pieces-jointes")
    public ResponseEntity<List<PieceJointeDto>> listerPiecesJointes(Principal principal, @PathVariable Integer id) {
        return ResponseEntity.ok(demandeService.listerPiecesJointes(principal.getName(), id));
    }

    @DeleteMapping("/pieces-jointes/{pieceId}")
    public ResponseEntity<Void> supprimerPieceJointe(Principal principal, @PathVariable Integer pieceId) {
        demandeService.supprimerPieceJointe(principal.getName(), pieceId);
        return ResponseEntity.noContent().build();
    }

    /** Statistiques du périmètre de l'utilisateur connecté (cadrage appliqué côté service). */
    @GetMapping("/statistiques")
    public ResponseEntity<StatistiquesDto> statistiques(Principal principal) {
        return ResponseEntity.ok(demandeService.statistiques(principal.getName()));
    }
}
