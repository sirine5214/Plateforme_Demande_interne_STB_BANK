import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { environment } from 'src/environments/environment';

export type Role = 'ADMINISTRATEUR' | 'CHEF_DE_PROJET' | 'DEVELOPPEUR' | 'DEMANDEUR';

export interface RegisterPayload {
  nom: string;
  email: string;
  motDePasse: string;
  role: Role;
}

export interface LoginPayload {
  email: string;
  motDePasse: string;
}

export interface AuthResponse {
  token: string;
  id: number;
  nom: string;
  email: string;
  role: Role;
  photoUrl: string | null;
}

export interface UpdateProfilePayload {
  nom: string;
  nouveauMotDePasse?: string;
}

export interface UserSummary {
  id: number;
  nom: string;
  email: string;
  role: Role;
  photoUrl: string | null;
  actif: boolean;
  dateCreation: string | null;
}

export interface CreateUserPayload {
  nom: string;
  email: string;
  motDePasse: string;
  role: Role;
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

export interface AdminUpdateUserPayload {
  nom: string;
  email: string;
  role: Role;
  actif: boolean;
  nouveauMotDePasse?: string;
}

export function resolveAvatarUrl(photoUrl: string | null | undefined): string {
  if (!photoUrl) {
    return 'assets/images/user/avatar-1.jpg';
  }
  return photoUrl.startsWith('http') ? photoUrl : `${environment.apiOrigin}${photoUrl}`;
}

const TOKEN_KEY = 'stb_token';
const USER_KEY = 'stb_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  currentUser = signal<Omit<AuthResponse, 'token'> | null>(this.readStoredUser());

  constructor(private http: HttpClient) {}

  register(payload: RegisterPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/register`, payload)
      .pipe(tap((res) => this.storeSession(res)));
  }

  login(payload: LoginPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/login`, payload)
      .pipe(tap((res) => this.storeSession(res)));
  }

  /** Demande un jeton de réinitialisation (BF 2.1.3). */
  motDePasseOublie(email: string): Observable<{ message: string; token: string }> {
    return this.http.post<{ message: string; token: string }>(`${environment.apiUrl}/auth/mot-de-passe-oublie`, { email });
  }

  reinitialiserMotDePasse(token: string, nouveauMotDePasse: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiUrl}/auth/reinitialiser-mot-de-passe`, {
      token,
      nouveauMotDePasse
    });
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getProfile(): Observable<Omit<AuthResponse, 'token'>> {
    return this.http.get<Omit<AuthResponse, 'token'>>(`${environment.apiUrl}/users/me`).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.currentUser.set(user);
      })
    );
  }

  updateProfile(payload: UpdateProfilePayload): Observable<Omit<AuthResponse, 'token'>> {
    return this.http.put<Omit<AuthResponse, 'token'>>(`${environment.apiUrl}/users/me`, payload).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.currentUser.set(user);
      })
    );
  }

  isAdmin(): boolean {
    return this.currentUser()?.role === 'ADMINISTRATEUR';
  }

  uploadPhoto(file: File): Observable<Omit<AuthResponse, 'token'>> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<Omit<AuthResponse, 'token'>>(`${environment.apiUrl}/users/me/photo`, formData).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.currentUser.set(user);
      })
    );
  }

  listUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${environment.apiUrl}/admin/users`);
  }

  /** Liste paginée et filtrable des utilisateurs (pagination côté serveur). */
  rechercherUsers(criteres: {
    role?: Role | '';
    actif?: boolean | null;
    motCle?: string;
    page?: number;
    taille?: number;
  }): Observable<PageResponse<UserSummary>> {
    let params = new HttpParams()
      .set('page', String(criteres.page ?? 0))
      .set('taille', String(criteres.taille ?? 8));

    if (criteres.role) params = params.set('role', criteres.role);
    if (criteres.actif !== null && criteres.actif !== undefined) params = params.set('actif', String(criteres.actif));
    if (criteres.motCle?.trim()) params = params.set('motCle', criteres.motCle.trim());

    return this.http.get<PageResponse<UserSummary>>(`${environment.apiUrl}/admin/users/recherche`, { params });
  }

  updateUserRole(id: number, role: Role): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${environment.apiUrl}/admin/users/${id}/role`, { role });
  }

  createUser(payload: CreateUserPayload): Observable<UserSummary> {
    return this.http.post<UserSummary>(`${environment.apiUrl}/admin/users`, payload);
  }

  updateUser(id: number, payload: AdminUpdateUserPayload): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${environment.apiUrl}/admin/users/${id}`, payload);
  }

  toggleUserStatus(id: number): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${environment.apiUrl}/admin/users/${id}/status`, {});
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/admin/users/${id}`);
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    const { token, ...user } = res;
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  private readStoredUser(): Omit<AuthResponse, 'token'> | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
