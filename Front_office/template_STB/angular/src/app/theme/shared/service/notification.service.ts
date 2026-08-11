import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { environment } from 'src/environments/environment';

import { TypeDemande } from './demande.service';

export type TypeNotification = 'DEMANDE' | 'MESSAGE';

export interface NotificationItem {
  id: number;
  message: string;
  dateEnvoi: string;
  lu: boolean;
  type: TypeNotification;
  demandeId: number | null;
  demandeNumero: string | null;
  typeDemande: TypeDemande | null;
  auteurNom: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);

  notifications = signal<NotificationItem[]>([]);
  nonLues = signal(0);

  charger(): Observable<NotificationItem[]> {
    return this.http.get<NotificationItem[]>(`${environment.apiUrl}/notifications`).pipe(
      tap((items) => {
        this.notifications.set(items);
        this.nonLues.set(items.filter((n) => !n.lu).length);
      })
    );
  }

  /** Insère en tête une notification reçue en direct par WebSocket. */
  ajouter(notification: NotificationItem): void {
    this.notifications.update((list) => [notification, ...list]);
    if (!notification.lu) {
      this.nonLues.update((compte) => compte + 1);
    }
  }

  compterNonLues(): Observable<{ nonLues: number }> {
    return this.http
      .get<{ nonLues: number }>(`${environment.apiUrl}/notifications/non-lues/compte`)
      .pipe(tap((res) => this.nonLues.set(res.nonLues)));
  }

  marquerCommeLue(id: number): Observable<NotificationItem> {
    return this.http.put<NotificationItem>(`${environment.apiUrl}/notifications/${id}/lue`, {}).pipe(
      tap((maj) => {
        this.notifications.update((list) => list.map((n) => (n.id === maj.id ? maj : n)));
        this.nonLues.update((compte) => Math.max(0, compte - 1));
      })
    );
  }

  toutMarquerCommeLu(): Observable<void> {
    return this.http.put<void>(`${environment.apiUrl}/notifications/tout-lire`, {}).pipe(
      tap(() => {
        this.notifications.update((list) => list.map((n) => ({ ...n, lu: true })));
        this.nonLues.set(0);
      })
    );
  }
}
