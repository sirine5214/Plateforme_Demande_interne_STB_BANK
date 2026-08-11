import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { AuthService, Role, UserSummary } from 'src/app/theme/shared/service/auth.service';
import { DemandeService, Statistiques } from 'src/app/theme/shared/service/demande.service';

const ROLE_LABELS: Record<Role, string> = {
  ADMINISTRATEUR: 'Administrateurs',
  CHEF_DE_PROJET: 'Chefs de projet',
  DEVELOPPEUR: 'Développeurs',
  DEMANDEUR: 'Demandeurs'
};

const ROLE_ICONES: Record<Role, string> = {
  ADMINISTRATEUR: 'feather icon-shield',
  CHEF_DE_PROJET: 'feather icon-briefcase',
  DEVELOPPEUR: 'feather icon-code',
  DEMANDEUR: 'feather icon-user'
};

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['../../demo/demandes/demandes.component.scss']
})
export class AdminDashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private demandeService = inject(DemandeService);

  loading = signal(true);
  users = signal<UserSummary[]>([]);
  stats = signal<Statistiques | null>(null);

  roleCards = computed(() => {
    const counts = this.users().reduce(
      (acc, u) => {
        acc[u.role] = (acc[u.role] ?? 0) + 1;
        return acc;
      },
      {} as Record<Role, number>
    );

    return (Object.keys(ROLE_LABELS) as Role[]).map((role) => ({
      role,
      label: ROLE_LABELS[role],
      icone: ROLE_ICONES[role],
      count: counts[role] ?? 0
    }));
  });

  totalUsers = computed(() => this.users().length);
  comptesActifs = computed(() => this.users().filter((u) => u.actif).length);
  comptesInactifs = computed(() => this.users().filter((u) => !u.actif).length);

  ngOnInit(): void {
    this.authService.listUsers().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    // L'administrateur supervise aussi l'activité : les statistiques complètent la vue comptes
    this.demandeService.statistiques().subscribe({
      next: (stats) => this.stats.set(stats),
      error: () => this.stats.set(null)
    });
  }
}
