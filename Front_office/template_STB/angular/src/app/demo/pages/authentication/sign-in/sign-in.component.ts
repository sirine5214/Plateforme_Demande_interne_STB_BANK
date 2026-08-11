// angular import
import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { email, form, FormField, minLength, required } from '@angular/forms/signals';

// project import
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService } from 'src/app/theme/shared/service/auth.service';
import { accueilParRole } from 'src/app/theme/shared/service/role.guard';

@Component({
  selector: 'app-sign-in',
  imports: [CommonModule, RouterModule, SharedModule, FormField],
  templateUrl: './sign-in.component.html',
  styleUrls: ['./sign-in.component.scss']
})
export class SignInComponent {
  private cd = inject(ChangeDetectorRef);
  private authService = inject(AuthService);
  private router = inject(Router);

  submitted = signal(false);
  loading = signal(false);
  error = signal('');
  showPassword = signal(false);

  loginModal = signal<{ email: string; password: string }>({
    email: '',
    password: ''
  });

  loginForm = form(this.loginModal, (schemaPath) => {
    required(schemaPath.email, { message: "L'email est obligatoire" });
    email(schemaPath.email, { message: 'Veuillez saisir une adresse email valide' });
    required(schemaPath.password, { message: 'Le mot de passe est obligatoire' });
    minLength(schemaPath.password, 8, { message: 'Le mot de passe doit contenir au moins 8 caractères' });
  });

  onSubmit(event: Event) {
    this.submitted.set(true);
    this.error.set('');
    event.preventDefault();

    if (this.loginForm().invalid()) {
      this.cd.detectChanges();
      return;
    }

    const credentials = this.loginModal();
    this.loading.set(true);

    this.authService
      .login({ email: credentials.email, motDePasse: credentials.password })
      .subscribe({
        next: (res) => {
          this.loading.set(false);
          this.router.navigate([accueilParRole(res.role)]);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message || 'Email ou mot de passe incorrect');
          this.cd.detectChanges();
        }
      });
  }

  togglePasswordVisibility() {
    this.showPassword.set(!this.showPassword());
  }
}
