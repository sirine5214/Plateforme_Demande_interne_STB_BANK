import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AdminUpdateUserPayload, AuthService, Role, UserSummary } from 'src/app/theme/shared/service/auth.service';

export const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: 'DEMANDEUR', label: 'Demandeur' },
  { value: 'DEVELOPPEUR', label: 'Développeur' },
  { value: 'CHEF_DE_PROJET', label: 'Chef de projet' },
  { value: 'ADMINISTRATEUR', label: 'Administrateur' }
];

const PAGE_SIZE = 8;

interface UserForm {
  nom: string;
  email: string;
  role: Role | '';
  motDePasse: string;
  actif: boolean;
}

const EMPTY_FORM: UserForm = { nom: '', email: '', role: '', motDePasse: '', actif: true };

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {
  private authService = inject(AuthService);

  roleOptions = ROLE_OPTIONS;
  pageSize = PAGE_SIZE;

  loading = signal(true);
  error = signal('');
  success = signal('');
  users = signal<UserSummary[]>([]);
  busyId = signal<number | null>(null);

  search = signal('');
  page = signal(1);

  // modal state
  modalOpen = signal(false);
  editingUser = signal<UserSummary | null>(null);
  saving = signal(false);
  formError = signal('');
  form: UserForm = { ...EMPTY_FORM };

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

  ngOnInit(): void {
    this.load();
  }

  /** Pagination et filtrage effectués côté serveur. */
  load(): void {
    this.loading.set(true);
    this.authService
      .rechercherUsers({ motCle: this.search(), page: this.page() - 1, taille: this.taillePage() })
      .subscribe({
        next: (resultat) => {
          this.users.set(resultat.contenu);
          this.totalPages.set(Math.max(1, resultat.totalPages));
          this.totalElements.set(resultat.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Impossible de charger la liste des utilisateurs');
          this.loading.set(false);
        }
      });
  }

  onSearchChange(value: string): void {
    this.search.set(value);
    this.page.set(1);

    if (this.rechercheTimer) {
      clearTimeout(this.rechercheTimer);
    }
    this.rechercheTimer = setTimeout(() => this.load(), 350);
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.page.set(page);
      this.load();
    }
  }

  onTaillePageChange(valeur: string): void {
    this.taillePage.set(Number(valeur));
    this.page.set(1);
    this.load();
  }

  roleLabel(role: Role): string {
    return ROLE_OPTIONS.find((option) => option.value === role)?.label ?? role;
  }

  // ---------- modal ----------

  openCreate(): void {
    this.editingUser.set(null);
    this.form = { ...EMPTY_FORM };
    this.formError.set('');
    this.modalOpen.set(true);
  }

  openEdit(user: UserSummary): void {
    this.editingUser.set(user);
    this.form = { nom: user.nom, email: user.email, role: user.role, motDePasse: '', actif: user.actif };
    this.formError.set('');
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
    this.saving.set(false);
  }

  submitForm(): void {
    if (!this.form.nom || !this.form.email || !this.form.role) {
      this.formError.set('Tous les champs marqués sont obligatoires');
      return;
    }

    const editing = this.editingUser();

    if (!editing && this.form.motDePasse.length < 8) {
      this.formError.set('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    if (editing && this.form.motDePasse && this.form.motDePasse.length < 8) {
      this.formError.set('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    this.formError.set('');
    this.saving.set(true);

    if (editing) {
      const payload: AdminUpdateUserPayload = {
        nom: this.form.nom,
        email: this.form.email,
        role: this.form.role as Role,
        actif: this.form.actif,
        nouveauMotDePasse: this.form.motDePasse || undefined
      };

      this.authService.updateUser(editing.id, payload).subscribe({
        next: (updated) => {
          this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
          this.saving.set(false);
          this.modalOpen.set(false);
          this.success.set('Utilisateur modifié avec succès');
        },
        error: (err) => {
          this.saving.set(false);
          this.formError.set(err?.error?.message || 'Erreur lors de la modification');
        }
      });
    } else {
      this.authService
        .createUser({
          nom: this.form.nom,
          email: this.form.email,
          motDePasse: this.form.motDePasse,
          role: this.form.role as Role
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.modalOpen.set(false);
            this.success.set('Utilisateur créé avec succès');
            this.page.set(1);
            this.load();
          },
          error: (err) => {
            this.saving.set(false);
            this.formError.set(err?.error?.message || 'Erreur lors de la création');
          }
        });
    }
  }

  // ---------- actions ----------

  toggleStatus(user: UserSummary): void {
    this.busyId.set(user.id);
    this.authService.toggleUserStatus(user.id).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.busyId.set(null);
        this.success.set(updated.actif ? 'Compte activé' : 'Compte désactivé');
      },
      error: () => {
        this.busyId.set(null);
        this.error.set("Erreur lors du changement d'état du compte");
      }
    });
  }

  deleteUser(user: UserSummary): void {
    if (!confirm(`Supprimer définitivement le compte de ${user.nom} ?`)) {
      return;
    }

    this.busyId.set(user.id);
    this.authService.deleteUser(user.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.success.set('Compte supprimé');
        if (this.users().length === 1 && this.page() > 1) {
          this.page.update((p) => p - 1);
        }
        this.load();
      },
      error: () => {
        this.busyId.set(null);
        this.error.set('Erreur lors de la suppression du compte');
      }
    });
  }
}
