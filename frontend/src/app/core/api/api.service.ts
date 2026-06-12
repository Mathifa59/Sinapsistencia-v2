import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Sobre de respuesta del backend — contrato idéntico al BFF legacy. */
export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: string;
}

/**
 * Equivalente del apiFetch<T>() legacy: desempaqueta {success,data} y lanza
 * Error(error) si success === false — así angular-query recibe data o error
 * con el mismo modelo mental que TanStack en el legacy.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  async get<T>(url: string, params?: Record<string, string | number | undefined>): Promise<T> {
    return this.unwrap(
      firstValueFrom(this.http.get<ApiEnvelope<T>>(url, { params: this.toParams(params) })),
    );
  }

  async post<T>(url: string, body?: unknown): Promise<T> {
    return this.unwrap(firstValueFrom(this.http.post<ApiEnvelope<T>>(url, body ?? {})));
  }

  async put<T>(url: string, body?: unknown): Promise<T> {
    return this.unwrap(firstValueFrom(this.http.put<ApiEnvelope<T>>(url, body ?? {})));
  }

  async patch<T>(url: string, body?: unknown): Promise<T> {
    return this.unwrap(firstValueFrom(this.http.patch<ApiEnvelope<T>>(url, body ?? {})));
  }

  async delete<T>(url: string): Promise<T> {
    return this.unwrap(firstValueFrom(this.http.delete<ApiEnvelope<T>>(url)));
  }

  /** Para multipart (avatar, adjuntos). */
  async postForm<T>(url: string, form: FormData): Promise<T> {
    return this.unwrap(firstValueFrom(this.http.post<ApiEnvelope<T>>(url, form)));
  }

  private async unwrap<T>(promise: Promise<ApiEnvelope<T>>): Promise<T> {
    let envelope: ApiEnvelope<T>;
    try {
      envelope = await promise;
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object') {
        const body = err.error as ApiEnvelope<unknown>;
        throw new Error(body.error ?? `Error ${err.status}`);
      }
      throw new Error('No se pudo conectar con el servidor');
    }
    if (!envelope.success) {
      throw new Error(envelope.error ?? 'Error desconocido');
    }
    return envelope.data as T;
  }

  private toParams(params?: Record<string, string | number | undefined>): HttpParams | undefined {
    if (!params) return undefined;
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return httpParams;
  }
}
