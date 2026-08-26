import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from 'src/app/theme/shared/service/auth.service';
import { PRIORITE_LABELS, Priorite, TYPE_LABELS, TypeDemande } from 'src/app/theme/shared/service/demande.service';
import {
  ConversionEmailPayload,
  EmailEntrant,
  EmailService,
  STATUT_EMAIL_LABELS,
  StatutEmail
} from 'src/app/theme/shared/service/email.service';

const PAGE_SIZE = 10;

/**
 * Boîte de réception partagée : les demandes internes arrivées par courriel.
 *
 * L'écran matérialise une règle de conception du back-office : un e-mail n'est jamais une
 * demande tant qu'un humain ne l'a pas qualifié. Le type et la priorité affichés ne sont que
 * des suggestions déduites des mots-clés, que le chef de projet confirme ou corrige.
 */
@Component({
  selector: 'app-boite-reception',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './boite-reception.component.html',
  styleUrls: ['./boite-reception.component.scss']
})
export class BoiteReceptionComponent implements OnInit {
  private emailService = inject(EmailService);
  private authService = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private router = inject(Router);

  typeLabels = TYPE_LABELS;
  prioriteLabels = PRIORITE_LABELS;
  statutLabels = STATUT_EMAIL_LABELS;

  typeOptions = Object.keys(TYPE_LABELS) as TypeDemande[];
  prioriteOptions = Object.keys(PRIORITE_LABELS) as Priorite[];
  onglets: StatutEmail[] = ['NON_TRAITE', 'CONVERTI', 'IGNORE'];

  loading = signal(true);
  error = signal('');
  success = signal('');
  busy = signal(false);

  emails = signal<EmailEntrant[]>([]);
  selection = signal<EmailEntrant | null>(null);
  ongletActif = signal<StatutEmail>('NON_TRAITE');
  nonTraites = signal(0);

  page = signal(0);
  totalPages = signal(0);
  totalElements = signal(0);

  estAdmin = computed(() => this.authService.currentUser()?.role === 'ADMINISTRATEUR');

  // ---- Formulaire de qualification ----
  conversionOpen = signal(false);
  formTitre = signal('');
  formDescription = signal('');
  formType = signal<TypeDemande>('ASSISTANCE');
  formPriorite = signal<Priorite>('MOYENNE');
  formDateLimite = signal('');

  // ---- Mise à l'écart ----
  ignorerOpen = signal(false);
  formMotif = signal('');

  ngOnInit(): void {
    this.charger();
    this.rafraichirCompteur();
  }

  changerOnglet(statut: StatutEmail): void {
    if (this.ongletActif() === statut) {
      return;
    }
    this.ongletActif.set(statut);
    this.page.set(0);
    this.selection.set(null);
    this.charger();
  }

  allerPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) {
      return;
    }
    this.page.set(page);
    this.charger();
  }

  selectionner(email: EmailEntrant): void {
    this.selection.set(this.selection()?.id === email.id ? null : email);
  }

  charger(): void {
    this.loading.set(true);
    this.emailService
      .lister(this.ongletActif(), this.page(), PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reponse) => {
          this.emails.set(reponse.contenu);
          this.totalPages.set(reponse.totalPages);
          this.totalElements.set(reponse.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(this.messageErreur(err, 'Chargement de la boîte impossible'));
          this.loading.set(false);
        }
      });
  }

  private rafraichirCompteur(): void {
    this.emailService
      .compterNonTraites()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reponse) => this.nonTraites.set(reponse.nonTraites),
        // Un compteur indisponible ne doit pas masquer la liste elle-même.
        error: () => this.nonTraites.set(0)
      });
  }

  relever(): void {
    this.busy.set(true);
    this.emailService
      .relever()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reponse) => {
          this.busy.set(false);
          this.success.set(
            reponse.importes > 0
              ? `${reponse.importes} nouvel(x) e-mail(s) importé(s).`
              : 'Aucun nouveau message sur la boîte.'
          );
          this.charger();
          this.rafraichirCompteur();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(this.messageErreur(err, 'Relève impossible'));
        }
      });
  }

  /**
   * Ouvre le formulaire de qualification pré-rempli à partir du message.
   *
   * Le titre reprend l'objet et la description le corps : dans la quasi-totalité des cas
   * l'agent n'a plus qu'à vérifier, ce qui est le but recherché — supprimer la ressaisie
   * manuelle qui se faisait jusqu'ici dans Excel.
   */
  ouvrirConversion(email: EmailEntrant): void {
    this.selection.set(email);
    this.formTitre.set(email.sujet);
    this.formDescription.set(email.corpsTexte ?? '');
    this.formType.set(email.typePropose ?? 'ASSISTANCE');
    this.formPriorite.set(email.prioriteProposee ?? 'MOYENNE');
    this.formDateLimite.set('');
    this.error.set('');
    this.conversionOpen.set(true);
  }

  confirmerConversion(): void {
    const email = this.selection();
    if (!email || !this.formTitre().trim()) {
      this.error.set('Le titre est obligatoire.');
      return;
    }

    const payload: ConversionEmailPayload = {
      titre: this.formTitre().trim(),
      description: this.formDescription().trim(),
      type: this.formType(),
      priorite: this.formPriorite(),
      dateLimite: this.formDateLimite() || null,
      // Le compte reconnu à partir de l'adresse d'expédition n'est qu'une suggestion :
      // s'il est absent, le serveur rattache la demande à l'agent qui qualifie.
      demandeurId: email.demandeurSuggereId
    };

    this.busy.set(true);
    this.emailService
      .convertir(email.id, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (demande) => {
          this.busy.set(false);
          this.conversionOpen.set(false);
          this.selection.set(null);
          this.success.set(`Demande ${demande.numero} créée à partir de l'e-mail.`);
          this.charger();
          this.rafraichirCompteur();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(this.messageErreur(err, 'Conversion impossible'));
        }
      });
  }

  ouvrirIgnorer(email: EmailEntrant): void {
    this.selection.set(email);
    this.formMotif.set('');
    this.error.set('');
    this.ignorerOpen.set(true);
  }

  confirmerIgnorer(): void {
    const email = this.selection();
    if (!email || !this.formMotif().trim()) {
      this.error.set('Le motif est obligatoire : il constitue la trace d’audit du rejet.');
      return;
    }

    this.busy.set(true);
    this.emailService
      .ignorer(email.id, this.formMotif().trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.ignorerOpen.set(false);
          this.selection.set(null);
          this.success.set('E-mail écarté. Il reste consultable dans l’onglet « Écartés ».');
          this.charger();
          this.rafraichirCompteur();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(this.messageErreur(err, 'Mise à l’écart impossible'));
        }
      });
  }

  ouvrirDemande(email: EmailEntrant): void {
    if (email.demandeId) {
      this.router.navigate(['/demandes'], { queryParams: { demande: email.demandeId } });
    }
  }

  /** Initiales de l'expéditeur, faute de photo pour un correspondant externe. */
  initiales(email: EmailEntrant): string {
    const source = email.expediteurNom?.trim() || email.expediteurEmail;
    const morceaux = source.split(/[\s.@_-]+/).filter(Boolean);
    return morceaux
      .slice(0, 2)
      .map((mot) => mot.charAt(0).toUpperCase())
      .join('');
  }

  tailleLisible(octets: number | null): string {
    if (!octets) {
      return '';
    }
    if (octets < 1024) {
      return `${octets} o`;
    }
    if (octets < 1024 * 1024) {
      return `${Math.round(octets / 1024)} Ko`;
    }
    return `${(octets / (1024 * 1024)).toFixed(1)} Mo`;
  }

  private messageErreur(err: unknown, defaut: string): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message ?? defaut;
  }
}
