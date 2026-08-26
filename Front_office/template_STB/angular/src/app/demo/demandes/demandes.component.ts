import { Component, DestroyRef, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { environment } from 'src/environments/environment';
import { AuthService, UserSummary, resolveAvatarUrl } from 'src/app/theme/shared/service/auth.service';
import { Message, MessagerieService } from 'src/app/theme/shared/service/messagerie.service';
import { TempsReelService } from 'src/app/theme/shared/service/temps-reel.service';
import {
  CreateDemandePayload,
  Demande,
  DemandeService,
  HistoriqueStatut,
  PRIORITE_LABELS,
  PieceJointe,
  Priorite,
  STATUT_LABELS,
  StatutDemande,
  TRANSITIONS_AUTORISEES,
  TYPE_LABELS,
  TypeDemande
} from 'src/app/theme/shared/service/demande.service';
import { GUIDES_TYPE } from './type-demande.guide';

const PAGE_SIZE = 8;

/** Message enrichi des informations d'affichage calculees une seule fois. */
interface MessageAffiche {
  message: Message;
  moi: boolean;
  /** Faux pour les messages consecutifs d'un meme auteur : l'avatar n'est alors pas repete. */
  avecAvatar: boolean;
}

/** Messages d'une meme journee, precedes de leur separateur. */
interface BlocJournalier {
  cle: string;
  libelle: string;
  messages: MessageAffiche[];
}

@Component({
  selector: 'app-demandes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './demandes.component.html',
  styleUrls: ['./demandes.component.scss']
})
export class DemandesComponent implements OnInit {
  private demandeService = inject(DemandeService);
  private authService = inject(AuthService);
  private messagerieService = inject(MessagerieService);
  private tempsReelService = inject(TempsReelService);
  private destroyRef = inject(DestroyRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  typeLabels = TYPE_LABELS;
  statutLabels = STATUT_LABELS;
  prioriteLabels = PRIORITE_LABELS;

  typeOptions = Object.keys(TYPE_LABELS) as TypeDemande[];
  prioriteOptions = Object.keys(PRIORITE_LABELS) as Priorite[];
  statutOptions = Object.keys(STATUT_LABELS) as StatutDemande[];

  loading = signal(true);
  error = signal('');
  success = signal('');
  busyId = signal<number | null>(null);
  demandes = signal<Demande[]>([]);
  affectables = signal<UserSummary[]>([]);

  // ---- critères de recherche (envoyés au serveur) ----
  search = signal('');
  filtreStatut = signal<StatutDemande | ''>('');
  filtrePriorite = signal<Priorite | ''>('');
  filtreType = signal<TypeDemande | ''>('');
  dateDebut = signal<string>('');
  dateFin = signal<string>('');

  // ---- pagination serveur (page affichée à partir de 1) ----
  page = signal(1);
  taillePage = signal(PAGE_SIZE);
  taillesDisponibles = [5, 8, 15, 30];
  totalPages = signal(1);
  totalElements = signal(0);
  pages = computed(() => Array.from({ length: this.totalPages() }, (_, i) => i + 1));

  /** Rang du premier élément affiché (0 si la liste est vide). */
  premierIndex = computed(() => (this.totalElements() === 0 ? 0 : (this.page() - 1) * this.taillePage() + 1));
  /** Rang du dernier élément affiché, borné au total. */
  dernierIndex = computed(() => Math.min(this.page() * this.taillePage(), this.totalElements()));

  private rechercheTimer: ReturnType<typeof setTimeout> | null = null;

  modalOpen = signal(false);
  saving = signal(false);
  formError = signal('');
  form: CreateDemandePayload = this.emptyForm();
  demandeEnEdition = signal<Demande | null>(null);
  /** Type courant du formulaire : pilote l'aide et les pièces attendues. */
  typeSelectionne = signal<TypeDemande>('DEVELOPPEMENT');
  /** Champs de la demande non modifiables (consultation par le responsable, par exemple). */
  lectureSeule = signal(false);
  /** Gestion des fichiers interdite (demande clôturée, ou utilisateur non concerné). */
  fichiersVerrouilles = signal(false);

  // historique (BF 2.3.2)
  historiqueOpen = signal(false);
  historiqueLoading = signal(false);
  historique = signal<HistoriqueStatut[]>([]);
  demandeHistorique = signal<Demande | null>(null);

  // messagerie demandeur ↔ responsable
  messages = signal<Message[]>([]);
  messagesLoading = signal(false);
  messageEnCours = '';
  envoiMessage = signal(false);

  private filRef = viewChild<ElementRef<HTMLDivElement>>('filDiscussion');

  /**
   * Fil decoupe par journee et par auteur.
   *
   * Regrouper evite la repetition de l'avatar et du nom a chaque ligne : sur une discussion
   * un peu longue, c'est ce qui distingue une conversation lisible d'un mur de vignettes.
   */
  filGroupe = computed<BlocJournalier[]>(() => {
    const blocs: BlocJournalier[] = [];
    let precedentAuteur: number | null = null;

    for (const message of this.messages()) {
      const cle = message.dateEnvoi.slice(0, 10);
      let bloc = blocs.at(-1);

      if (!bloc || bloc.cle !== cle) {
        bloc = { cle, libelle: this.libelleJour(cle), messages: [] };
        blocs.push(bloc);
        // Changement de jour : le premier message reaffiche toujours son avatar.
        precedentAuteur = null;
      }

      bloc.messages.push({
        message,
        moi: this.estMonMessage(message),
        avecAvatar: message.expediteurId !== precedentAuteur
      });
      precedentAuteur = message.expediteurId;
    }

    return blocs;
  });

  // pièces jointes (BF 2.2.6)
  piecesOpen = signal(false);
  piecesLoading = signal(false);
  piecesEnvoi = signal(false);
  pieces = signal<PieceJointe[]>([]);
  demandePieces = signal<Demande | null>(null);

  role = computed(() => this.authService.currentUser()?.role ?? null);
  estDemandeur = computed(() => this.role() === 'DEMANDEUR');
  estDeveloppeur = computed(() => this.role() === 'DEVELOPPEUR');
  estChef = computed(() => this.role() === 'CHEF_DE_PROJET' || this.role() === 'ADMINISTRATEUR');

  titrePage = computed(() => {
    if (this.estDemandeur()) return 'Mes demandes';
    if (this.estDeveloppeur()) return 'Demandes qui me sont affectées';
    return 'Suivi des demandes';
  });

  /** Une demande clôturée est figée : le serveur refuse toute réaffectation. */
  estCloturee(demande: Demande): boolean {
    return demande.statut === 'TERMINEE' || demande.statut === 'REJETEE';
  }

  /**
   * Statuts proposés pour une demande donnée : croisement du cycle de vie
   * (transitions autorisées depuis son statut courant) et des droits du rôle.
   */
  statutsProposes(demande: Demande): StatutDemande[] {
    if (this.estCloturee(demande)) {
      return [];
    }

    const suivants = TRANSITIONS_AUTORISEES[demande.statut];

    if (this.estChef()) {
      return suivants;
    }
    if (this.estDeveloppeur() && this.estResponsable(demande)) {
      // Le développeur fait avancer le traitement, mais ne clôture ni ne rejette
      return suivants.filter((s) => s !== 'TERMINEE' && s !== 'REJETEE');
    }
    return [];
  }

  ngOnInit(): void {
    // Les entrées du menu latéral pilotent le filtre via l'URL (ex. « À valider »).
    this.route.queryParamMap.subscribe((params) => {
      this.filtreStatut.set((params.get('statut') as StatutDemande | null) ?? '');
      // Mot-clé transmis par la recherche globale de la barre de navigation
      this.search.set(params.get('motCle') ?? '');
      this.page.set(1);
      this.charger();

      // Ouverture directe d'une demande, typiquement depuis une notification
      const demandeId = params.get('demandeId');
      if (demandeId) {
        this.ouvrirDepuisNotification(Number(demandeId), params.get('section'));
        this.nettoyerParametres(['demandeId', 'section']);
      }

      if (params.get('nouvelle') === '1') {
        this.openCreate();
        // On retire le drapeau de l'URL pour que le menu puisse rouvrir la fenêtre ensuite.
        this.nettoyerParametres(['nouvelle']);
      }
    });

    if (this.estChef()) {
      this.demandeService.affectables().subscribe({
        next: (users) => this.affectables.set(users),
        error: () => this.affectables.set([])
      });
    }

    // Message reçu en direct : on l'ajoute au fil si la demande concernée est ouverte
    this.tempsReelService.connecter();
    this.tempsReelService.message$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((message) => {
      if (this.demandeEnEdition()?.id === message.demandeId) {
        this.messages.update((fil) => [...fil, message]);
        this.messagerieService.marquerLus(message.demandeId).subscribe({ error: () => undefined });
      }
    });
  }

  /** Interroge le serveur avec les critères et la page courants. */
  charger(): void {
    this.loading.set(true);

    this.demandeService
      .rechercher({
        statut: this.filtreStatut(),
        priorite: this.filtrePriorite(),
        type: this.filtreType(),
        motCle: this.search(),
        dateDebut: this.dateDebut() || null,
        dateFin: this.dateFin() || null,
        page: this.page() - 1,
        taille: this.taillePage()
      })
      .subscribe({
        next: (resultat) => {
          this.demandes.set(resultat.contenu);
          this.totalPages.set(Math.max(1, resultat.totalPages));
          this.totalElements.set(resultat.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Impossible de charger les demandes');
          this.loading.set(false);
        }
      });
  }

  /** Recherche texte : on attend une pause de frappe avant d'appeler le serveur. */
  onSearchChange(value: string): void {
    this.search.set(value);
    this.page.set(1);

    if (this.rechercheTimer) {
      clearTimeout(this.rechercheTimer);
    }
    this.rechercheTimer = setTimeout(() => this.charger(), 350);
  }

  onFiltreChange(): void {
    this.page.set(1);
    this.charger();
  }

  onStatutFiltreChange(value: string): void {
    this.filtreStatut.set(value as StatutDemande | '');
    this.onFiltreChange();
  }

  onPrioriteFiltreChange(value: string): void {
    this.filtrePriorite.set(value as Priorite | '');
    this.onFiltreChange();
  }

  onTypeFiltreChange(value: string): void {
    this.filtreType.set(value as TypeDemande | '');
    this.onFiltreChange();
  }

  onDateDebutChange(value: string): void {
    this.dateDebut.set(value);
    this.onFiltreChange();
  }

  onDateFinChange(value: string): void {
    this.dateFin.set(value);
    this.onFiltreChange();
  }

  reinitialiserFiltres(): void {
    this.search.set('');
    this.filtreStatut.set('');
    this.filtrePriorite.set('');
    this.filtreType.set('');
    this.dateDebut.set('');
    this.dateFin.set('');
    this.page.set(1);
    this.charger();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.page.set(page);
      this.charger();
    }
  }

  onTaillePageChange(valeur: string): void {
    this.taillePage.set(Number(valeur));
    this.page.set(1);
    this.charger();
  }

  // ---------- création / modification ----------

  /** Consigne et pièces attendues correspondant au type actuellement sélectionné. */
  guideCourant = computed(() => GUIDES_TYPE[this.typeSelectionne()]);

  openCreate(): void {
    this.demandeEnEdition.set(null);
    this.lectureSeule.set(false);
    this.fichiersVerrouilles.set(false);
    this.form = this.emptyForm();
    // On pré-remplit la description avec le canevas du type par défaut.
    this.form.description = GUIDES_TYPE[this.form.type].canevas;
    this.typeSelectionne.set(this.form.type);
    this.pieces.set([]);
    this.formError.set('');
    this.modalOpen.set(true);
  }

  /**
   * Ouvre le dossier de la demande. Les champs ne sont éditables que par ceux qui en ont le droit ;
   * pour le responsable, la fenêtre sert à consulter la demande et à gérer ses fichiers.
   */
  openEdit(demande: Demande): void {
    this.demandePieces.set(null);
    this.lectureSeule.set(!this.peutModifier(demande));
    this.fichiersVerrouilles.set(!this.peutGererFichiers(demande));
    this.demandeEnEdition.set(demande);
    this.form = {
      titre: demande.titre,
      description: demande.description ?? '',
      priorite: demande.priorite,
      type: demande.type,
      dateLimite: demande.dateLimite
    };
    this.typeSelectionne.set(demande.type);
    this.formError.set('');
    this.modalOpen.set(true);

    // Les pièces jointes sont chargées avec la demande : on les contrôle depuis le même écran.
    this.chargerPieces(demande.id);
    this.chargerMessages(demande.id);
  }

  /**
   * Ouvre la fiche d'une demande désignée par son identifiant.
   * La demande peut ne pas figurer sur la page courante : on la récupère donc au besoin.
   */
  private ouvrirDepuisNotification(demandeId: number, section: string | null): void {
    const dejaChargee = this.demandes().find((d) => d.id === demandeId);

    if (dejaChargee) {
      this.openEdit(dejaChargee);
      this.faireDefilerVers(section);
      return;
    }

    this.demandeService.consulter(demandeId).subscribe({
      next: (demande) => {
        this.openEdit(demande);
        this.faireDefilerVers(section);
      },
      error: () => this.error.set("Cette demande n'est plus accessible")
    });
  }

  /** Amène le fil de discussion dans le champ de vision une fois la fenêtre rendue. */
  private faireDefilerVers(section: string | null): void {
    if (section !== 'messages') {
      return;
    }

    setTimeout(() => {
      document.getElementById('section-discussion')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 250);
  }

  /** Retire des paramètres de l'URL sans recharger la page. */
  private nettoyerParametres(cles: string[]): void {
    const queryParams: Record<string, null> = {};
    cles.forEach((cle) => (queryParams[cle] = null));

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  // ---------- messagerie ----------

  private chargerMessages(demandeId: number): void {
    this.messages.set([]);
    this.messageEnCours = '';
    this.messagesLoading.set(true);

    this.messagerieService.lister(demandeId).subscribe({
      next: (fil) => {
        this.messages.set(fil);
        this.messagesLoading.set(false);
        this.defilerEnBas();
        this.messagerieService.marquerLus(demandeId).subscribe({ error: () => undefined });
      },
      error: () => {
        this.messages.set([]);
        this.messagesLoading.set(false);
      }
    });
  }

  envoyerMessage(): void {
    const demande = this.demandeEnEdition();
    const contenu = this.messageEnCours.trim();

    if (!demande || !contenu) {
      return;
    }

    this.envoiMessage.set(true);

    this.messagerieService.envoyer(demande.id, contenu).subscribe({
      next: (message) => {
        this.messages.update((fil) => [...fil, message]);
        this.messageEnCours = '';
        this.envoiMessage.set(false);
        this.defilerEnBas();
      },
      error: (err) => {
        this.envoiMessage.set(false);
        this.error.set(err?.error?.message || "Erreur lors de l'envoi du message");
      }
    });
  }

  estMonMessage(message: Message): boolean {
    return message.expediteurId === this.authService.currentUser()?.id;
  }

  avatarMessage(message: Message): string {
    return resolveAvatarUrl(message.expediteurPhotoUrl);
  }

  /** « Aujourd'hui » et « Hier » plutot qu'une date : c'est ainsi qu'on situe une conversation recente. */
  private libelleJour(cleIso: string): string {
    const jour = new Date(cleIso + 'T00:00:00');
    const aujourdhui = new Date();
    aujourdhui.setHours(0, 0, 0, 0);

    const ecartJours = Math.round((aujourdhui.getTime() - jour.getTime()) / 86_400_000);
    if (ecartJours === 0) {
      return "Aujourd'hui";
    }
    if (ecartJours === 1) {
      return 'Hier';
    }
    return jour.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  /**
   * Ramene le fil sur le dernier message.
   *
   * Le setTimeout laisse Angular appliquer le rendu : sans ce report, scrollHeight vaudrait
   * encore la hauteur d'avant l'ajout et le defilement s'arreterait trop haut.
   */
  private defilerEnBas(): void {
    setTimeout(() => {
      const element = this.filRef()?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    });
  }

  /**
   * Au changement de type, on applique le canevas correspondant —
   * sans écraser une description déjà rédigée par l'utilisateur.
   */
  onTypeFormulaireChange(type: string): void {
    const nouveauType = type as TypeDemande;
    const ancienCanevas = GUIDES_TYPE[this.typeSelectionne()].canevas;

    this.form.type = nouveauType;
    this.typeSelectionne.set(nouveauType);

    if (!this.form.description?.trim() || this.form.description.trim() === ancienCanevas.trim()) {
      this.form.description = GUIDES_TYPE[nouveauType].canevas;
    }
  }

  private chargerPieces(demandeId: number): void {
    this.piecesLoading.set(true);
    this.pieces.set([]);

    this.demandeService.listerPiecesJointes(demandeId).subscribe({
      next: (fichiers) => {
        this.pieces.set(fichiers);
        this.piecesLoading.set(false);
      },
      error: () => {
        this.pieces.set([]);
        this.piecesLoading.set(false);
      }
    });
  }

  closeModal(): void {
    this.modalOpen.set(false);
    this.saving.set(false);
  }

  submitForm(): void {
    if (!this.form.titre || !this.form.priorite || !this.form.type) {
      this.formError.set('Le titre, la priorité et le type sont obligatoires');
      return;
    }

    const enEdition = this.demandeEnEdition();

    // Certains types ne sont pas traitables sans justificatif (bug à reproduire, accès à valider).
    if (enEdition && this.guideCourant().pieceObligatoire && this.pieces().length === 0) {
      this.formError.set(
        `Une pièce jointe est requise pour ce type de demande (${this.guideCourant().piecesAttendues.join(', ')})`
      );
      return;
    }

    this.formError.set('');
    this.saving.set(true);

    if (enEdition) {
      this.demandeService.modifier(enEdition.id, this.form).subscribe({
        next: (maj) => {
          this.demandes.update((list) => list.map((d) => (d.id === maj.id ? maj : d)));
          this.saving.set(false);
          this.modalOpen.set(false);
          this.success.set(`Demande ${maj.numero} modifiée`);
        },
        error: (err) => {
          this.saving.set(false);
          this.formError.set(err?.error?.message || 'Erreur lors de la modification de la demande');
        }
      });
      return;
    }

    this.demandeService.creer(this.form).subscribe({
      next: (demande) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.success.set(`Demande ${demande.numero} créée avec succès`);
        this.page.set(1);
        this.charger();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(err?.error?.message || 'Erreur lors de la création de la demande');
      }
    });
  }

  // ---------- historique ----------

  ouvrirHistorique(demande: Demande): void {
    this.demandeHistorique.set(demande);
    this.historique.set([]);
    this.historiqueLoading.set(true);
    this.historiqueOpen.set(true);

    this.demandeService.historique(demande.id).subscribe({
      next: (lignes) => {
        this.historique.set(lignes);
        this.historiqueLoading.set(false);
      },
      error: (err) => {
        this.historiqueLoading.set(false);
        this.historiqueOpen.set(false);
        this.error.set(err?.error?.message || "Impossible de charger l'historique");
      }
    });
  }

  fermerHistorique(): void {
    this.historiqueOpen.set(false);
  }

  // ---------- pièces jointes ----------

  ouvrirPieces(demande: Demande): void {
    this.demandePieces.set(demande);
    this.pieces.set([]);
    this.piecesLoading.set(true);
    this.piecesOpen.set(true);

    this.demandeService.listerPiecesJointes(demande.id).subscribe({
      next: (fichiers) => {
        this.pieces.set(fichiers);
        this.piecesLoading.set(false);
      },
      error: (err) => {
        this.piecesLoading.set(false);
        this.piecesOpen.set(false);
        this.error.set(err?.error?.message || 'Impossible de charger les pièces jointes');
      }
    });
  }

  fermerPieces(): void {
    this.piecesOpen.set(false);
    // Évite que le formulaire de modification n'envoie ensuite vers cette demande.
    this.demandePieces.set(null);
  }

  onFichierSelectionne(event: Event): void {
    const input = event.target as HTMLInputElement;
    const fichier = input.files?.[0];
    // L'envoi est possible depuis la fenêtre dédiée comme depuis le formulaire de modification.
    const demande = this.demandePieces() ?? this.demandeEnEdition();

    if (!fichier || !demande) {
      return;
    }

    this.piecesEnvoi.set(true);

    this.demandeService.ajouterPieceJointe(demande.id, fichier).subscribe({
      next: (piece) => {
        this.pieces.update((list) => [piece, ...list]);
        this.piecesEnvoi.set(false);
        input.value = '';
      },
      error: (err) => {
        this.piecesEnvoi.set(false);
        input.value = '';
        this.error.set(err?.error?.message || "Erreur lors de l'envoi du fichier");
      }
    });
  }

  supprimerPiece(piece: PieceJointe): void {
    if (!confirm(`Supprimer la pièce jointe « ${piece.nomFichier} » ?`)) {
      return;
    }

    this.demandeService.supprimerPieceJointe(piece.id).subscribe({
      next: () => this.pieces.update((list) => list.filter((p) => p.id !== piece.id)),
      error: (err) => this.error.set(err?.error?.message || 'Erreur lors de la suppression du fichier')
    });
  }

  urlFichier(piece: PieceJointe): string {
    return piece.url.startsWith('http') ? piece.url : `${this.origineApi}${piece.url}`;
  }

  tailleLisible(octets: number | null): string {
    if (!octets) return '';
    if (octets < 1024) return `${octets} o`;
    if (octets < 1024 * 1024) return `${Math.round(octets / 1024)} Ko`;
    return `${(octets / (1024 * 1024)).toFixed(1)} Mo`;
  }

  // ---------- actions ----------

  changerStatut(demande: Demande, statut: string): void {
    if (!statut || statut === demande.statut) {
      return;
    }

    this.busyId.set(demande.id);
    this.demandeService.changerStatut(demande.id, statut as StatutDemande).subscribe({
      next: (maj) => {
        this.demandes.update((list) => list.map((d) => (d.id === maj.id ? maj : d)));
        this.busyId.set(null);
        this.success.set(`Statut mis à jour : ${this.statutLabels[maj.statut]}`);
      },
      error: (err) => {
        this.busyId.set(null);
        this.error.set(err?.error?.message || 'Erreur lors du changement de statut');
      }
    });
  }

  affecter(demande: Demande, responsableId: string): void {
    if (!responsableId) {
      return;
    }

    this.busyId.set(demande.id);
    this.demandeService.affecter(demande.id, Number(responsableId)).subscribe({
      next: (maj) => {
        this.demandes.update((list) => list.map((d) => (d.id === maj.id ? maj : d)));
        this.busyId.set(null);
        this.success.set(`Demande affectée à ${maj.responsableNom}`);
      },
      error: (err) => {
        this.busyId.set(null);
        this.error.set(err?.error?.message || "Erreur lors de l'affectation");
      }
    });
  }

  supprimer(demande: Demande): void {
    if (!confirm(`Supprimer définitivement la demande ${demande.numero} ?`)) {
      return;
    }

    this.busyId.set(demande.id);
    this.demandeService.supprimer(demande.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.success.set('Demande supprimée');
        if (this.demandes().length === 1 && this.page() > 1) {
          this.page.update((p) => p - 1);
        }
        this.charger();
      },
      error: (err) => {
        this.busyId.set(null);
        this.error.set(err?.error?.message || 'Erreur lors de la suppression');
      }
    });
  }

  peutSupprimer(demande: Demande): boolean {
    return this.estChef() || (this.estDemandeur() && demande.statut === 'NOUVELLE');
  }

  /** Une demande est modifiable par son auteur tant qu'elle est nouvelle, ou par le chef de projet. */
  peutModifier(demande: Demande): boolean {
    return this.estChef() || (this.estDemandeur() && demande.statut === 'NOUVELLE');
  }

  /** L'utilisateur courant est-il le responsable désigné de cette demande ? */
  estResponsable(demande: Demande): boolean {
    const id = this.authService.currentUser()?.id;
    return !!id && demande.responsableId === id;
  }

  /**
   * Les fichiers sont gérés par le responsable (livrables, correctifs), par le chef de projet,
   * et par le demandeur tant que sa demande n'est pas prise en charge.
   * Une demande clôturée est figée.
   */
  peutGererFichiers(demande: Demande): boolean {
    if (demande.statut === 'TERMINEE' || demande.statut === 'REJETEE') {
      return this.estChef();
    }
    return this.estChef() || this.estResponsable(demande) || (this.estDemandeur() && demande.statut === 'NOUVELLE');
  }

  private get origineApi(): string {
    return environment.apiOrigin;
  }

  private emptyForm(): CreateDemandePayload {
    return { titre: '', description: '', priorite: 'MOYENNE', type: 'DEVELOPPEMENT', dateLimite: null };
  }
}
