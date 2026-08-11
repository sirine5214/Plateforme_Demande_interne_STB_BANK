import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from 'src/environments/environment';
import { UserSummary } from './auth.service';

export type TypeDemande = 'DEVELOPPEMENT' | 'CORRECTION_BUG' | 'MAINTENANCE' | 'ASSISTANCE' | 'CREATION_ACCES' | 'EVOLUTION';
export type StatutDemande = 'NOUVELLE' | 'EN_COURS' | 'EN_VALIDATION' | 'TERMINEE' | 'REJETEE';
export type Priorite = 'BASSE' | 'MOYENNE' | 'HAUTE' | 'CRITIQUE';

export interface Demande {
  id: number;
  numero: string;
  titre: string;
  description: string | null;
  priorite: Priorite;
  statut: StatutDemande;
  type: TypeDemande;
  dateCreation: string;
  dateLimite: string | null;
  dateCloture: string | null;
  demandeurId: number;
  demandeurNom: string;
  responsableId: number | null;
  responsableNom: string | null;
}

export interface CreateDemandePayload {
  titre: string;
  description?: string;
  priorite: Priorite;
  type: TypeDemande;
  dateLimite?: string | null;
}

export interface HistoriqueStatut {
  id: number;
  ancienStatut: StatutDemande | null;
  nouveauStatut: StatutDemande;
  dateChangement: string;
  auteurNom: string;
}

export interface Statistiques {
  total: number;
  ouvertes: number;
  cloturees: number;
  parStatut: Record<string, number>;
  parPriorite: Record<string, number>;
  parResponsable: Record<string, number>;
  parType: Record<string, number>;
  enRetard: number;
  nonAffectees: number;
  evolutionMensuelle: Record<string, number>;
  tempsMoyenTraitementHeures: number;
}

export interface PageResponse<T> {
  contenu: T[];
  page: number;
  taille: number;
  totalElements: number;
  totalPages: number;
  premiere: boolean;
  derniere: boolean;
}

export interface CritèresRecherche {
  statut?: StatutDemande | '';
  priorite?: Priorite | '';
  type?: TypeDemande | '';
  responsableId?: number | null;
  motCle?: string;
  dateDebut?: string | null;
  dateFin?: string | null;
  page?: number;
  taille?: number;
}

export interface PieceJointe {
  id: number;
  nomFichier: string;
  url: string;
  tailleOctets: number | null;
  dateAjout: string;
}

export const TYPE_LABELS: Record<TypeDemande, string> = {
  DEVELOPPEMENT: 'Développement',
  CORRECTION_BUG: 'Correction de bug',
  MAINTENANCE: 'Maintenance',
  ASSISTANCE: 'Assistance',
  CREATION_ACCES: "Création d'accès",
  EVOLUTION: 'Évolution'
};

export const STATUT_LABELS: Record<StatutDemande, string> = {
  NOUVELLE: 'Nouvelle',
  EN_COURS: 'En cours',
  EN_VALIDATION: 'En validation',
  TERMINEE: 'Terminée',
  REJETEE: 'Rejetée'
};

/**
 * Cycle de vie autorisé, aligné sur le backend (StatutDemande.transitionsAutorisees).
 * Un statut final ne mène nulle part : la demande est figée.
 */
export const TRANSITIONS_AUTORISEES: Record<StatutDemande, StatutDemande[]> = {
  NOUVELLE: ['EN_COURS', 'REJETEE'],
  EN_COURS: ['EN_VALIDATION', 'NOUVELLE', 'REJETEE'],
  EN_VALIDATION: ['TERMINEE', 'EN_COURS', 'REJETEE'],
  TERMINEE: [],
  REJETEE: []
};

export const PRIORITE_LABELS: Record<Priorite, string> = {
  BASSE: 'Basse',
  MOYENNE: 'Moyenne',
  HAUTE: 'Haute',
  CRITIQUE: 'Critique'
};

@Injectable({ providedIn: 'root' })
export class DemandeService {
  private http = inject(HttpClient);

  lister(): Observable<Demande[]> {
    return this.http.get<Demande[]>(`${environment.apiUrl}/demandes`);
  }

  /** Récupère une demande précise — utilisé pour l'ouvrir depuis une notification. */
  consulter(id: number): Observable<Demande> {
    return this.http.get<Demande>(`${environment.apiUrl}/demandes/${id}`);
  }

  /** Recherche multicritère paginée côté serveur (BF 2.6). */
  rechercher(criteres: CritèresRecherche): Observable<PageResponse<Demande>> {
    let params = new HttpParams()
      .set('page', String(criteres.page ?? 0))
      .set('taille', String(criteres.taille ?? 8));

    if (criteres.statut) params = params.set('statut', criteres.statut);
    if (criteres.priorite) params = params.set('priorite', criteres.priorite);
    if (criteres.type) params = params.set('type', criteres.type);
    if (criteres.responsableId) params = params.set('responsableId', String(criteres.responsableId));
    if (criteres.motCle?.trim()) params = params.set('motCle', criteres.motCle.trim());
    if (criteres.dateDebut) params = params.set('dateDebut', criteres.dateDebut);
    if (criteres.dateFin) params = params.set('dateFin', criteres.dateFin);

    return this.http.get<PageResponse<Demande>>(`${environment.apiUrl}/demandes/recherche`, { params });
  }

  // ---- Pièces jointes ----

  listerPiecesJointes(demandeId: number): Observable<PieceJointe[]> {
    return this.http.get<PieceJointe[]>(`${environment.apiUrl}/demandes/${demandeId}/pieces-jointes`);
  }

  ajouterPieceJointe(demandeId: number, fichier: File): Observable<PieceJointe> {
    const formData = new FormData();
    formData.append('file', fichier);
    return this.http.post<PieceJointe>(`${environment.apiUrl}/demandes/${demandeId}/pieces-jointes`, formData);
  }

  supprimerPieceJointe(pieceId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/demandes/pieces-jointes/${pieceId}`);
  }

  creer(payload: CreateDemandePayload): Observable<Demande> {
    return this.http.post<Demande>(`${environment.apiUrl}/demandes`, payload);
  }

  modifier(id: number, payload: CreateDemandePayload): Observable<Demande> {
    return this.http.put<Demande>(`${environment.apiUrl}/demandes/${id}`, payload);
  }

  historique(id: number): Observable<HistoriqueStatut[]> {
    return this.http.get<HistoriqueStatut[]>(`${environment.apiUrl}/demandes/${id}/historique`);
  }

  changerStatut(id: number, statut: StatutDemande): Observable<Demande> {
    return this.http.put<Demande>(`${environment.apiUrl}/demandes/${id}/statut`, { statut });
  }

  affecter(id: number, responsableId: number): Observable<Demande> {
    return this.http.put<Demande>(`${environment.apiUrl}/demandes/${id}/responsable`, { responsableId });
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/demandes/${id}`);
  }

  statistiques(): Observable<Statistiques> {
    return this.http.get<Statistiques>(`${environment.apiUrl}/demandes/statistiques`);
  }

  affectables(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${environment.apiUrl}/users/affectables`);
  }
}
