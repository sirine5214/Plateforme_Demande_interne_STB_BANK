import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';

import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService } from 'src/app/theme/shared/service/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, SharedModule],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  /** L'écran se déroule en deux temps : demande du jeton puis saisie du nouveau mot de passe. */
  etape = signal<'demande' | 'reinitialisation'>('demande');

  loading = signal(false);
  error = signal('');
  success = signal('');

  email = '';
  token = '';
  nouveauMotDePasse = '';
  confirmation = '';

  demanderLien(): void {
    if (!this.email) {
      this.error.set("L'email est obligatoire");
      return;
    }

    this.error.set('');
    this.success.set('');
    this.loading.set(true);

    this.authService.motDePasseOublie(this.email).subscribe({
      next: (res) => {
        this.loading.set(false);
        // Sans service d'e-mail, le backend renvoie le jeton : on le pré-remplit pour l'utilisateur.
        this.token = res.token;
        this.etape.set('reinitialisation');
        this.success.set('Un jeton de réinitialisation a été généré. Il est valable 30 minutes.');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Aucun compte associé à cet email');
      }
    });
  }

  reinitialiser(): void {
    if (!this.token || !this.nouveauMotDePasse) {
      this.error.set('Le jeton et le nouveau mot de passe sont obligatoires');
      return;
    }

    if (this.nouveauMotDePasse.length < 8) {
      this.error.set('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    if (this.nouveauMotDePasse !== this.confirmation) {
      this.error.set('Les deux mots de passe ne correspondent pas');
      return;
    }

    this.error.set('');
    this.loading.set(true);

    this.authService.reinitialiserMotDePasse(this.token, this.nouveauMotDePasse).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set('Mot de passe réinitialisé. Redirection vers la connexion...');
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Le lien de réinitialisation est invalide ou expiré');
      }
    });
  }
}
