// angular import
import { Component, DestroyRef, OnInit, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterModule } from '@angular/router';

// bootstrap import
import { NgbDropdownConfig } from '@ng-bootstrap/ng-bootstrap';

// project import
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService, resolveAvatarUrl } from 'src/app/theme/shared/service/auth.service';
import { NotificationItem, NotificationService } from 'src/app/theme/shared/service/notification.service';
import { TYPE_LABELS } from 'src/app/theme/shared/service/demande.service';
import { TempsReelService } from 'src/app/theme/shared/service/temps-reel.service';

@Component({
  selector: 'app-nav-right',
  imports: [SharedModule, RouterModule],
  templateUrl: './nav-right.component.html',
  styleUrls: ['./nav-right.component.scss'],
  providers: [NgbDropdownConfig]
})
export class NavRightComponent implements OnInit {
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);
  private tempsReelService = inject(TempsReelService);
  private destroyRef = inject(DestroyRef);
  private router = inject(Router);

  currentUser = this.authService.currentUser;
  avatarUrl = computed(() => resolveAvatarUrl(this.currentUser()?.photoUrl));

  notifications = this.notificationService.notifications;
  nonLues = this.notificationService.nonLues;
  typeLabels = TYPE_LABELS;

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      return;
    }

    this.notificationService.charger().subscribe({ error: () => undefined });

    // Flux temps réel : la cloche se met à jour sans rechargement
    this.tempsReelService.connecter();
    this.tempsReelService.notification$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((notif) => {
      this.notificationService.ajouter(notif);
    });
  }

  marquerCommeLue(id: number, event: Event): void {
    event.stopPropagation();
    this.notificationService.marquerCommeLue(id).subscribe({ error: () => undefined });
  }

  toutMarquerCommeLu(event: Event): void {
    event.preventDefault();
    this.notificationService.toutMarquerCommeLu().subscribe({ error: () => undefined });
  }

  /**
   * Ouvre la demande concernée par la notification.
   * Pour un message, on demande en plus l'ouverture directe du fil de discussion.
   */
  ouvrirNotification(notif: NotificationItem): void {
    if (!notif.lu) {
      this.notificationService.marquerCommeLue(notif.id).subscribe({ error: () => undefined });
    }

    if (!notif.demandeId) {
      return;
    }

    this.router.navigate(['/demandes'], {
      queryParams: {
        demandeId: notif.demandeId,
        section: notif.type === 'MESSAGE' ? 'messages' : null
      }
    });
  }

  logout() {
    this.tempsReelService.deconnecter();
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
