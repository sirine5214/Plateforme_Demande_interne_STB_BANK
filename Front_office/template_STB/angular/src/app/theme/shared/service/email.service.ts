import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from 'src/environments/environment';
import { Demande, PageResponse, Priorite, TypeDemande } from './demande.service';

export type StatutEmail = 'NON_TRAITE' | 'CONVERTI' | 'IGNORE';

export interface PieceJointeEmail {
  id: number;
  nomFichierOrigine: string;
  cheminFichier: string;
  contentType: string | null;
  tailleOctets: number | null;
}

/**
 * E-mail reçu sur la boîte partagée de la direction.
 *
 * Le corps est toujours du texte brut : le serveur ne conserve aucun HTML d'expéditeur,
 * ce qui interdit qu'un message malveillant injecte du balisage dans cette interface.
 */
export interface EmailEntrant {
  id: number;
  expediteurEmail: string;
  expediteurNom: string | null;
  sujet: string;
  corpsTexte: string | null;
  dateReception: string;
  statut: StatutEmail;
  /** Suggestions produites par la pré-qualification, à confirmer ou corriger. */
  typePropose: TypeDemande | null;
  prioriteProposee: Priorite | null;
  numeroDemande: string | null;
  demandeId: number | null;
  traiteParNom: string | null;
  dateTraitement: string | null;
  motifIgnore: string | null;
  /** Compte reconnu à partir de l'adresse d'expédition, s'il existe. */
  demandeurSuggereId: number | null;
  demandeurSuggereNom: string | null;
  piecesJointes: PieceJointeEmail[];
}

export interface ConversionEmailPayload {
  titre: string;
  description: string;
  priorite: Priorite;
  type: TypeDemande;
  dateLimite?: string | null;
  demandeurId?: number | null;
}

export const STATUT_EMAIL_LABELS: Record<StatutEmail, string> = {
  NON_TRAITE: 'À qualifier',
  CONVERTI: 'Convertis en demande',
  IGNORE: 'Écartés'
};

/**
 * Boîte de réception partagée : consultation et qualification des e-mails entrants.
 * Réservée aux administrateurs et chefs de projet, contrôle appliqué côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class EmailService {
  private http = inject(HttpClient);

  lister(statut: StatutEmail, page = 0, taille = 10): Observable<PageResponse<EmailEntrant>> {
    const params = new HttpParams().set('statut', statut).set('page', page).set('taille', taille);
    return this.http.get<PageResponse<EmailEntrant>>(`${environment.apiUrl}/emails`, { params });
  }

  consulter(id: number): Observable<EmailEntrant> {
    return this.http.get<EmailEntrant>(`${environment.apiUrl}/emails/${id}`);
  }

  compterNonTraites(): Observable<{ nonTraites: number }> {
    return this.http.get<{ nonTraites: number }>(`${environment.apiUrl}/emails/non-traites/compte`);
  }

  convertir(id: number, payload: ConversionEmailPayload): Observable<Demande> {
    return this.http.post<Demande>(`${environment.apiUrl}/emails/${id}/convertir`, payload);
  }

  ignorer(id: number, motif: string): Observable<EmailEntrant> {
    return this.http.post<EmailEntrant>(`${environment.apiUrl}/emails/${id}/ignorer`, { motif });
  }

  /** Relève immédiate de la boîte, sans attendre le passage planifié. Réservée aux administrateurs. */
  relever(): Observable<{ importes: number }> {
    return this.http.post<{ importes: number }>(`${environment.apiUrl}/emails/relever`, {});
  }
}
