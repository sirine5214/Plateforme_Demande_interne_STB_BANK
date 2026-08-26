// Angular Import
import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

// project import
import { AdminComponent } from './theme/layout/admin/admin.component';
import { GuestComponent } from './theme/layout/guest/guest.component';
import { authGuard } from './theme/shared/service/auth.guard';
import { adminGuard } from './theme/shared/service/admin.guard';
import { roleGuard } from './theme/shared/service/role.guard';

const routes: Routes = [
  {
    path: '',
    component: AdminComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: '/demandes',
        pathMatch: 'full'
      },
      {
        path: 'demandes',
        canActivate: [roleGuard(['DEMANDEUR', 'DEVELOPPEUR', 'CHEF_DE_PROJET', 'ADMINISTRATEUR'])],
        loadComponent: () => import('./demo/demandes/demandes.component').then((c) => c.DemandesComponent)
      },
      {
        // Boîte partagée : seuls administrateurs et chefs de projet qualifient les e-mails.
        // Le serveur applique la même restriction, ce garde n'evite qu'un aller-retour inutile.
        path: 'emails',
        canActivate: [roleGuard(['CHEF_DE_PROJET', 'ADMINISTRATEUR'])],
        loadComponent: () => import('./demo/emails/boite-reception.component').then((c) => c.BoiteReceptionComponent)
      },
      {
        // Ouvert à tous : le périmètre des statistiques est cadré par rôle côté serveur
        path: 'statistiques',
        canActivate: [roleGuard(['DEMANDEUR', 'DEVELOPPEUR', 'CHEF_DE_PROJET', 'ADMINISTRATEUR'])],
        loadComponent: () => import('./demo/demandes/statistiques.component').then((c) => c.StatistiquesComponent)
      },
      {
        path: 'analytics',
        loadComponent: () => import('./demo/dashboard/dash-analytics.component').then((c) => c.DashAnalyticsComponent)
      },
      {
        path: 'component',
        loadChildren: () => import('./demo/ui-element/ui-basic.module').then((m) => m.UiBasicModule)
      },
      {
        path: 'chart',
        loadComponent: () => import('./demo/chart-maps/core-apex.component').then((c) => c.CoreApexComponent)
      },
      {
        path: 'forms',
        loadComponent: () => import('./demo/forms/form-elements/form-elements.component').then((c) => c.FormElementsComponent)
      },
      {
        path: 'tables',
        loadComponent: () => import('./demo/tables/tbl-bootstrap/tbl-bootstrap.component').then((c) => c.TblBootstrapComponent)
      },
      {
        path: 'sample-page',
        loadComponent: () => import('./demo/other/sample-page/sample-page.component').then((c) => c.SamplePageComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./demo/pages/profile/profile.component').then((c) => c.ProfileComponent)
      },
      {
        path: 'admin/dashboard',
        canActivate: [adminGuard],
        loadComponent: () => import('./admin/dashboard/admin-dashboard.component').then((c) => c.AdminDashboardComponent)
      },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        loadComponent: () => import('./admin/users/user-management.component').then((c) => c.UserManagementComponent)
      }
    ]
  },
  {
    path: '',
    component: GuestComponent,
    children: [
      {
        path: 'register',
        loadComponent: () => import('./demo/pages/authentication/sign-up/sign-up.component').then((c) => c.SignUpComponent)
      },
      {
        path: 'login',
        loadComponent: () => import('./demo/pages/authentication/sign-in/sign-in.component').then((c) => c.SignInComponent)
      },
      {
        path: 'mot-de-passe-oublie',
        loadComponent: () =>
          import('./demo/pages/authentication/forgot-password/forgot-password.component').then((c) => c.ForgotPasswordComponent)
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
