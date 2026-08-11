// angular import
import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

// project import
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { Demande, DemandeService, STATUT_LABELS, TYPE_LABELS } from 'src/app/theme/shared/service/demande.service';

/** Nombre de suggestions affichées dans le volet ; le reste est accessible via « Voir tout ». */
const LIMITE_SUGGESTIONS = 6;

@Component({
  selector: 'app-nav-search',
  imports: [SharedModule],
  templateUrl: './nav-search.component.html',
  styleUrls: ['./nav-search.component.scss']
})
export class NavSearchComponent {
  private demandeService = inject(DemandeService);
  private router = inject(Router);
  private hote = inject(ElementRef<HTMLElement>);

  searchOn = false;

  terme = signal('');
  resultats = signal<Demande[]>([]);
  total = signal(0);
  recherche = signal(false);

  statutLabels = STATUT_LABELS;
  typeLabels = TYPE_LABELS;

  private timer: ReturnType<typeof setTimeout> | null = null;

  ouvrir(): void {
    this.searchOn = true;
  }

  fermer(): void {
    this.searchOn = false;
    this.terme.set('');
    this.resultats.set([]);
    this.total.set(0);
  }

  /** La frappe est temporisée pour ne pas interroger le serveur à chaque caractère. */
  onTermeChange(valeur: string): void {
    this.terme.set(valeur);

    if (this.timer) {
      clearTimeout(this.timer);
    }

    if (valeur.trim().length < 2) {
      this.resultats.set([]);
      this.total.set(0);
      return;
    }

    this.timer = setTimeout(() => this.lancerRecherche(), 300);
  }

  private lancerRecherche(): void {
    this.recherche.set(true);

    // Le périmètre est imposé côté serveur : chacun ne trouve que ses propres demandes
    this.demandeService.rechercher({ motCle: this.terme(), page: 0, taille: LIMITE_SUGGESTIONS }).subscribe({
      next: (page) => {
        this.resultats.set(page.contenu);
        this.total.set(page.totalElements);
        this.recherche.set(false);
      },
      error: () => {
        this.resultats.set([]);
        this.total.set(0);
        this.recherche.set(false);
      }
    });
  }

  ouvrirDemande(demande: Demande): void {
    this.router.navigate(['/demandes'], { queryParams: { demandeId: demande.id } });
    this.fermer();
  }

  voirTousLesResultats(): void {
    this.router.navigate(['/demandes'], { queryParams: { motCle: this.terme() } });
    this.fermer();
  }

  /** Un clic hors du composant referme le volet de recherche. */
  @HostListener('document:click', ['$event'])
  onClicDocument(event: MouseEvent): void {
    if (this.searchOn && !this.hote.nativeElement.contains(event.target as Node)) {
      this.fermer();
    }
  }

  @HostListener('document:keydown.escape')
  onEchap(): void {
    if (this.searchOn) {
      this.fermer();
    }
  }
}
