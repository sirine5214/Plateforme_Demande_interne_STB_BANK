import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApexOptions, NgApexchartsModule } from 'ng-apexcharts';

import { AuthService } from 'src/app/theme/shared/service/auth.service';
import { EmailService } from 'src/app/theme/shared/service/email.service';
import {
  DemandeService,
  PRIORITE_LABELS,
  Priorite,
  STATUT_LABELS,
  Statistiques,
  StatutDemande,
  TYPE_LABELS,
  TypeDemande
} from 'src/app/theme/shared/service/demande.service';

/** Palette STB utilisée par tous les graphiques. */
const COULEURS_STB = ['#0080c9', '#0477bf', '#05f2db', '#0511f2', '#8a94a0'];

/**
 * Couleur portée par le sens, non par la position dans la palette.
 *
 * Appliquer une palette décorative dans l'ordre des clés fait qu'une même catégorie change
 * de couleur d'un écran à l'autre, et qu'un statut d'échec peut se retrouver en vert. Ici,
 * le rouge signale toujours un rejet, le vert toujours une clôture réussie.
 */
const COULEURS_STATUT: Record<string, string> = {
  NOUVELLE: '#4680ff',
  EN_COURS: '#0080c9',
  EN_VALIDATION: '#ff9800',
  TERMINEE: '#4caf50',
  REJETEE: '#ff5252'
};

/** Même principe pour l'urgence : la teinte chauffe à mesure que la priorité monte. */
const COULEURS_PRIORITE: Record<string, string> = {
  BASSE: '#a8b1bb',
  MOYENNE: '#4680ff',
  HAUTE: '#ff9800',
  CRITIQUE: '#ff5252'
};

/** Réglages communs à tous les graphiques : une seule source pour la cohérence visuelle. */
const BASE_GRAPHIQUE = {
  fontFamily: 'inherit',
  foreColor: '#8a94a0',
  toolbar: { show: false }
};

@Component({
  selector: 'app-statistiques',
  standalone: true,
  imports: [CommonModule, RouterModule, NgApexchartsModule],
  templateUrl: './statistiques.component.html',
  styleUrls: ['./demandes.component.scss']
})
export class StatistiquesComponent implements OnInit {
  private demandeService = inject(DemandeService);
  private authService = inject(AuthService);
  private emailService = inject(EmailService);

  loading = signal(true);
  error = signal('');
  stats = signal<Statistiques | null>(null);

  /** E-mails en attente de qualification, seulement pertinent pour un superviseur. */
  emailsNonTraites = signal(0);

  role = computed(() => this.authService.currentUser()?.role ?? null);
  estDemandeur = computed(() => this.role() === 'DEMANDEUR');
  estDeveloppeur = computed(() => this.role() === 'DEVELOPPEUR');
  estSuperviseur = computed(() => this.role() === 'CHEF_DE_PROJET' || this.role() === 'ADMINISTRATEUR');

  /** Le tableau de bord porte le vocabulaire du rôle qui le consulte. */
  titre = computed(() => {
    if (this.estDemandeur()) return 'Mon tableau de bord';
    if (this.estDeveloppeur()) return 'Mon activité';
    return 'Tableau de bord';
  });

  sousTitre = computed(() => {
    if (this.estDemandeur()) return 'Suivi de vos demandes';
    if (this.estDeveloppeur()) return 'Demandes qui vous sont affectées';
    return "Vue d'ensemble de l'activité";
  });

  libelleTotal = computed(() => {
    if (this.estDemandeur()) return 'Mes demandes';
    if (this.estDeveloppeur()) return 'Demandes affectées';
    return 'Demandes au total';
  });

  parResponsable = computed(() => Object.entries(this.stats()?.parResponsable ?? {}));
  parType = computed(() => Object.entries(this.stats()?.parType ?? {}));

  /** Taux de clôture, indicateur de progression global. */
  tauxCloture = computed(() => {
    const s = this.stats();
    if (!s || s.total === 0) return 0;
    return Math.round((s.cloturees / s.total) * 100);
  });

  /** Évolution mensuelle du nombre de demandes (BF 2.4.3). */
  graphiqueEvolution = computed<ApexOptions>(() => {
    const evolution = this.stats()?.evolutionMensuelle ?? {};
    return {
      chart: { ...BASE_GRAPHIQUE, type: 'area', height: 300 },
      colors: [COULEURS_STB[0]],
      dataLabels: { enabled: false },
      stroke: { curve: 'smooth', width: 3 },
      fill: { type: 'gradient', gradient: { shadeIntensity: 1, opacityFrom: 0.4, opacityTo: 0.05 } },
      // Seules les horizontales sont conservees : les verticales n'aident pas a lire une
      // courbe et ajoutent du bruit sur une serie mensuelle.
      grid: { borderColor: '#edf0f3', strokeDashArray: 4, xaxis: { lines: { show: false } } },
      series: [{ name: 'Demandes créées', data: Object.values(evolution) }],
      xaxis: { categories: Object.keys(evolution), axisBorder: { show: false }, axisTicks: { show: false } },
      yaxis: { labels: { formatter: (valeur: number) => String(Math.round(valeur)) } },
      tooltip: { theme: 'light', y: { formatter: (valeur: number) => `${Math.round(valeur)} demande(s)` } }
    };
  });

  /** Répartition par statut (BF 2.4.1). */
  graphiqueStatuts = computed<ApexOptions>(() => {
    const parStatut = this.stats()?.parStatut ?? {};
    return {
      chart: { ...BASE_GRAPHIQUE, type: 'donut', height: 300 },
      colors: Object.keys(parStatut).map((cle) => COULEURS_STATUT[cle] ?? COULEURS_STB[4]),
      labels: Object.keys(parStatut).map((cle) => this.libelleStatut(cle)),
      series: Object.values(parStatut),
      legend: { position: 'bottom', markers: { size: 6 } },
      dataLabels: { enabled: true, formatter: (pourcentage: number) => `${Math.round(pourcentage)} %` },
      // Le total au centre evite au lecteur d'additionner mentalement les parts.
      plotOptions: {
        pie: {
          donut: {
            size: '68%',
            labels: {
              show: true,
              value: { fontSize: '24px', fontWeight: 600, color: '#262b31' },
              total: { show: true, label: 'Total', color: '#8a94a0' }
            }
          }
        }
      },
      tooltip: { y: { formatter: (valeur: number) => `${valeur} demande(s)` } }
    };
  });

  /** Répartition par priorité (BF 2.4.2). */
  graphiquePriorites = computed<ApexOptions>(() => {
    const parPriorite = this.stats()?.parPriorite ?? {};
    return {
      chart: { ...BASE_GRAPHIQUE, type: 'bar', height: 300 },
      colors: Object.keys(parPriorite).map((cle) => COULEURS_PRIORITE[cle] ?? COULEURS_STB[4]),
      // distributed : chaque barre prend sa propre couleur, sinon Apex applique la premiere
      // couleur a toute la serie et l'echelle d'urgence disparait.
      plotOptions: { bar: { borderRadius: 6, columnWidth: '45%', distributed: true } },
      dataLabels: { enabled: false },
      grid: { borderColor: '#edf0f3', strokeDashArray: 4, xaxis: { lines: { show: false } } },
      // Legende masquee : les libelles figurent deja sous chaque barre.
      legend: { show: false },
      series: [{ name: 'Demandes', data: Object.values(parPriorite) }],
      xaxis: {
        categories: Object.keys(parPriorite).map((cle) => this.libellePriorite(cle)),
        axisBorder: { show: false },
        axisTicks: { show: false }
      },
      yaxis: { labels: { formatter: (valeur: number) => String(Math.round(valeur)) } },
      tooltip: { y: { formatter: (valeur: number) => `${valeur} demande(s)` } }
    };
  });

  ngOnInit(): void {
    if (this.estSuperviseur()) {
      this.emailService.compterNonTraites().subscribe({
        next: (reponse) => this.emailsNonTraites.set(reponse.nonTraites),
        // Un compteur indisponible ne doit pas empecher l'affichage des statistiques.
        error: () => this.emailsNonTraites.set(0)
      });
    }

    this.demandeService.statistiques().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les statistiques');
        this.loading.set(false);
      }
    });
  }

  /** Les clés viennent du backend sous forme de chaînes : on les traduit sans indexer avec `any`. */
  libelleStatut(cle: string): string {
    return STATUT_LABELS[cle as StatutDemande] ?? cle;
  }

  libellePriorite(cle: string): string {
    return PRIORITE_LABELS[cle as Priorite] ?? cle;
  }

  libelleType(cle: string): string {
    return TYPE_LABELS[cle as TypeDemande] ?? cle;
  }

  /** Largeur de barre en pourcentage du total. */
  pourcentage(valeur: number): number {
    const total = this.stats()?.total ?? 0;
    return total === 0 ? 0 : Math.round((valeur / total) * 100);
  }

  /** Temps moyen de traitement exprimé en jours/heures selon la durée (BF 2.4.4). */
  tempsMoyenLisible(): string {
    const heures = this.stats()?.tempsMoyenTraitementHeures ?? 0;
    if (heures === 0) return '—';
    if (heures < 24) return `${heures} h`;
    return `${(heures / 24).toFixed(1)} j`;
  }
}
