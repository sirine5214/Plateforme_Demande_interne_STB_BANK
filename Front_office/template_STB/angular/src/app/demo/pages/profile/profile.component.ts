import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService, resolveAvatarUrl } from 'src/app/theme/shared/service/auth.service';

const ROLE_LABELS: Record<string, string> = {
  ADMINISTRATEUR: 'Administrateur',
  CHEF_DE_PROJET: 'Chef de projet',
  DEVELOPPEUR: 'Développeur',
  DEMANDEUR: 'Demandeur'
};

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, SharedModule],
  templateUrl: './profile.component.html'
})
export class ProfileComponent implements OnInit {
  private authService = inject(AuthService);

  loading = signal(true);
  saving = signal(false);
  uploadingPhoto = signal(false);
  success = signal('');
  error = signal('');

  nom = '';
  nouveauMotDePasse = '';

  get email(): string {
    return this.authService.currentUser()?.email ?? '';
  }

  get roleLabel(): string {
    const role = this.authService.currentUser()?.role;
    return role ? ROLE_LABELS[role] : '';
  }

  get avatarUrl(): string {
    return resolveAvatarUrl(this.authService.currentUser()?.photoUrl);
  }

  ngOnInit(): void {
    this.authService.getProfile().subscribe({
      next: (user) => {
        this.nom = user.nom;
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger le profil');
      }
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.success.set('');
    this.error.set('');
    this.uploadingPhoto.set(true);

    this.authService.uploadPhoto(file).subscribe({
      next: () => {
        this.uploadingPhoto.set(false);
        this.success.set('Photo de profil mise à jour');
        input.value = '';
      },
      error: (err) => {
        this.uploadingPhoto.set(false);
        this.error.set(err?.error?.message || "Erreur lors de l'envoi de la photo");
        input.value = '';
      }
    });
  }

  onSubmit(): void {
    this.success.set('');
    this.error.set('');
    this.saving.set(true);

    this.authService
      .updateProfile({
        nom: this.nom,
        nouveauMotDePasse: this.nouveauMotDePasse || undefined
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.nouveauMotDePasse = '';
          this.success.set('Profil mis à jour avec succès');
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err?.error?.message || 'Erreur lors de la mise à jour du profil');
        }
      });
  }
}
