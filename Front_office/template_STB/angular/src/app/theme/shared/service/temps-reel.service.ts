import { Injectable, inject } from '@angular/core';
import { Subject } from 'rxjs';

import { environment } from 'src/environments/environment';
import { AuthService } from './auth.service';
import { NotificationItem } from './notification.service';
import { Message } from './messagerie.service';

type TypeEvenement = 'NOTIFICATION' | 'MESSAGE';

interface EvenementTempsReel {
  type: TypeEvenement;
  charge: unknown;
}

/**
 * Connexion WebSocket native (sans STOMP ni SockJS) pour recevoir notifications
 * et messages en direct. Se reconnecte automatiquement en cas de coupure.
 */
@Injectable({ providedIn: 'root' })
export class TempsReelService {
  private authService = inject(AuthService);

  private socket: WebSocket | null = null;
  private reconnexionTimer: ReturnType<typeof setTimeout> | null = null;
  private delaiReconnexion = 2000;
  private fermetureVoulue = false;

  /** Notification poussée par le serveur. */
  notification$ = new Subject<NotificationItem>();
  /** Message de discussion poussé par le serveur. */
  message$ = new Subject<Message>();

  connecter(): void {
    const token = this.authService.getToken();
    if (!token || this.socket) {
      return;
    }

    this.fermetureVoulue = false;
    const base = environment.apiOrigin.replace(/^http/, 'ws');
    this.socket = new WebSocket(`${base}/ws/temps-reel?token=${encodeURIComponent(token)}`);

    this.socket.onmessage = (event) => this.traiter(event.data);

    this.socket.onopen = () => {
      this.delaiReconnexion = 2000;
    };

    this.socket.onclose = () => {
      this.socket = null;
      if (!this.fermetureVoulue && this.authService.isAuthenticated()) {
        this.planifierReconnexion();
      }
    };

    // onerror est suivi d'un onclose : la reconnexion y est déjà gérée
    this.socket.onerror = () => this.socket?.close();
  }

  deconnecter(): void {
    this.fermetureVoulue = true;

    if (this.reconnexionTimer) {
      clearTimeout(this.reconnexionTimer);
      this.reconnexionTimer = null;
    }

    this.socket?.close();
    this.socket = null;
  }

  private traiter(donnees: string): void {
    let evenement: EvenementTempsReel;
    try {
      evenement = JSON.parse(donnees);
    } catch {
      return;
    }

    if (evenement.type === 'NOTIFICATION') {
      this.notification$.next(evenement.charge as NotificationItem);
    } else if (evenement.type === 'MESSAGE') {
      this.message$.next(evenement.charge as Message);
    }
  }

  /** Reconnexion avec délai croissant, plafonné à 30 secondes. */
  private planifierReconnexion(): void {
    if (this.reconnexionTimer) {
      return;
    }

    this.reconnexionTimer = setTimeout(() => {
      this.reconnexionTimer = null;
      this.delaiReconnexion = Math.min(this.delaiReconnexion * 2, 30000);
      this.connecter();
    }, this.delaiReconnexion);
  }
}
