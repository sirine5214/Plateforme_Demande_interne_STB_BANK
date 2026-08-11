import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, Role } from './auth.service';

/** Page d'accueil par défaut de chaque rôle. */
export function accueilParRole(role: Role | undefined | null): string {
  switch (role) {
    case 'ADMINISTRATEUR':
      return '/admin/dashboard';
    case 'CHEF_DE_PROJET':
      return '/statistiques';
    case 'DEVELOPPEUR':
    case 'DEMANDEUR':
      // Chacun arrive sur son tableau de bord, qui résume son périmètre
      return '/statistiques';
    default:
      return '/login';
  }
}

/** Restreint une route à une liste de rôles (RBAC côté front). */
export function roleGuard(rolesAutorises: Role[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      router.navigate(['/login']);
      return false;
    }

    const role = authService.currentUser()?.role;

    if (!role || !rolesAutorises.includes(role)) {
      router.navigate([accueilParRole(role)]);
      return false;
    }

    return true;
  };
}
