// angular import
import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { email, form, FormField, minLength, required } from '@angular/forms/signals';

// project import
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService, Role } from 'src/app/theme/shared/service/auth.service';
import { accueilParRole } from 'src/app/theme/shared/service/role.guard';

export const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: 'DEMANDEUR', label: 'Demandeur' },
  { value: 'DEVELOPPEUR', label: 'Développeur' },
  { value: 'CHEF_DE_PROJET', label: 'Chef de projet' },
  { value: 'ADMINISTRATEUR', label: 'Administrateur' }
];

@Component({
  selector: 'app-sign-up',
  imports: [CommonModule, RouterModule, SharedModule, FormField],
  templateUrl: './sign-up.component.html',
  styleUrls: ['./sign-up.component.scss']
})
export class SignUpComponent {
  private cd = inject(ChangeDetectorRef);
  private authService = inject(AuthService);
  private router = inject(Router);

  submitted = signal(false);
  loading = signal(false);
  error = signal('');
  showPassword = signal(false);

  roleOptions = ROLE_OPTIONS;

  registerModel = signal<{ email: string; password: string; username: string; role: Role | '' }>({
    email: '',
    password: '',
    username: '',
    role: ''
  });

  registerForm = form(this.registerModel, (schemaPath) => {
    required(schemaPath.email, { message: "L'email est obligatoire" });
    email(schemaPath.email, { message: 'Veuillez saisir une adresse email valide' });
    required(schemaPath.password, { message: 'Le mot de passe est obligatoire' });
    minLength(schemaPath.password, 8, { message: 'Le mot de passe doit contenir au moins 8 caractères' });
    required(schemaPath.username, { message: 'Le nom est obligatoire' });
    required(schemaPath.role, { message: 'Le rôle est obligatoire' });
  });

  onSubmit(event: Event) {
    this.submitted.set(true);
    this.error.set('');
    event.preventDefault();

    if (this.registerForm().invalid()) {
      this.cd.detectChanges();
      return;
    }

    const values = this.registerModel();
    this.loading.set(true);

    this.authService
      .register({ nom: values.username, email: values.email, motDePasse: values.password, role: values.role as Role })
      .subscribe({
        next: (res) => {
          this.loading.set(false);
          this.router.navigate([accueilParRole(res.role)]);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message || "Une erreur est survenue lors de l'inscription");
          this.cd.detectChanges();
        }
      });
  }

  togglePasswordVisibility() {
    this.showPassword.set(!this.showPassword());
  }
}
