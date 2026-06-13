import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiService } from '../api/api.service';
import type { UserRole } from '../../shared/constants';

/** Shape del usuario que devuelve /api/auth/* (idéntico al legacy). */
export interface AuthUser {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  isActive: boolean;
  createdAt: string;
  avatar: string | null;
}

interface LoginResponse {
  user: AuthUser;
  token: string;
}

/** Body de POST /api/auth/register — espeja RegisterRequest del backend. */
export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
  role: 'doctor' | 'lawyer';
  cmp?: string;
  specialty?: string;
  hospital?: string;
  cab?: string;
  legalSpecialties?: string[];
  medicalAreas?: string[];
}

/**
 * Reemplaza el auth.store de Zustand con signals. El JWT viaja en cookie
 * httpOnly (la maneja el navegador); aquí solo vive el estado del usuario.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);

  private readonly _user = signal<AuthUser | null>(null);
  private readonly _isLoading = signal(false);
  private readonly _hydrated = signal(false);

  readonly user = this._user.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly role = computed(() => this._user()?.role ?? null);

  async login(email: string, password: string): Promise<AuthUser> {
    this._isLoading.set(true);
    try {
      const result = await this.api.post<LoginResponse>('/api/auth/login', { email, password });
      this._user.set(result.user);
      this._hydrated.set(true);
      return result.user;
    } finally {
      this._isLoading.set(false);
    }
  }

  async loginByRole(role: UserRole): Promise<AuthUser> {
    this._isLoading.set(true);
    try {
      const result = await this.api.post<LoginResponse>('/api/auth/login', { role });
      this._user.set(result.user);
      this._hydrated.set(true);
      return result.user;
    } finally {
      this._isLoading.set(false);
    }
  }

  async register(payload: RegisterPayload): Promise<void> {
    this._isLoading.set(true);
    try {
      await this.api.post<{ message: string }>('/api/auth/register', payload);
    } finally {
      this._isLoading.set(false);
    }
  }

  async logout(): Promise<void> {
    try {
      await this.api.post('/api/auth/logout');
    } catch {
      // la cookie expira igual; no bloquear el logout local
    }
    this._user.set(null);
  }

  /**
   * Hidrata la sesión desde la cookie httpOnly vía /api/auth/me.
   * La usan los guards en el primer acceso (reemplaza el rehydrate de Zustand).
   */
  async hydrate(): Promise<AuthUser | null> {
    if (this._hydrated()) {
      return this._user();
    }
    try {
      const user = await this.api.get<AuthUser>('/api/auth/me');
      this._user.set(user);
      return user;
    } catch {
      this._user.set(null);
      return null;
    } finally {
      this._hydrated.set(true);
    }
  }
}
