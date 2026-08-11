import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from 'src/environments/environment';

export interface Message {
  id: number;
  contenu: string;
  dateEnvoi: string;
  lu: boolean;
  expediteurId: number;
  expediteurNom: string;
  expediteurPhotoUrl: string | null;
  demandeId: number;
  demandeNumero: string;
}

/** Fil de discussion rattaché à une demande, entre le demandeur et le responsable. */
@Injectable({ providedIn: 'root' })
export class MessagerieService {
  private http = inject(HttpClient);

  lister(demandeId: number): Observable<Message[]> {
    return this.http.get<Message[]>(`${environment.apiUrl}/demandes/${demandeId}/messages`);
  }

  envoyer(demandeId: number, contenu: string): Observable<Message> {
    return this.http.post<Message>(`${environment.apiUrl}/demandes/${demandeId}/messages`, { contenu });
  }

  marquerLus(demandeId: number): Observable<void> {
    return this.http.put<void>(`${environment.apiUrl}/demandes/${demandeId}/messages/lus`, {});
  }
}
