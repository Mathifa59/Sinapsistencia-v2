import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { DoctorCardDto } from './generated/model/doctorCardDto';
import type { LawyerCardDto } from './generated/model/lawyerCardDto';
import type { RecommendationDto } from './generated/model/recommendationDto';
import type { RecommendationsResponse } from './generated/model/recommendationsResponse';
import type { ContactRequestResponse } from './generated/model/contactRequestResponse';
import type { CreateContactRequestBody } from './generated/model/createContactRequestBody';
import type { RespondContactRequestBody } from './generated/model/respondContactRequestBody';
import type { GenerateRecommendationsBody } from './generated/model/generateRecommendationsBody';

export interface ContactRequestListParams {
  lawyerId?: string;
  doctorId?: string;
  status?: string;
  [key: string]: string | number | undefined;
}

/** H-02: ejecución de matching persistida -- IDs reales, nunca "rec-...". */
export interface RecommendationRunDto {
  runId?: string;
  caseId?: string;
  status?: 'processing' | 'completed' | 'failed' | string;
  origin?: 'ml' | 'fallback' | null;
  modelUsed?: string | null;
  pipelineVersion?: string;
  createdAt?: string;
  completedAt?: string | null;
  weights?: Record<string, number>;
  recommendations?: RecommendationDto[];
  advisoryNote?: string;
}

/** H-02: fila de historial -- sin el detalle de recomendaciones. */
export interface RecommendationRunSummaryDto {
  runId?: string;
  caseId?: string;
  status?: string;
  origin?: string | null;
  modelUsed?: string | null;
  pipelineVersion?: string;
  createdAt?: string;
  completedAt?: string | null;
  candidateCount?: number | null;
  resultCount?: number | null;
}

@Injectable({ providedIn: 'root' })
export class MatchingApi {
  private readonly api = inject(ApiService);

  doctors(): Promise<DoctorCardDto[]> {
    return this.api.get<DoctorCardDto[]>('/api/matching/doctors');
  }

  lawyers(): Promise<LawyerCardDto[]> {
    return this.api.get<LawyerCardDto[]>('/api/matching/lawyers');
  }

  /**
   * Con doctorId: última ejecución GUARDADA (nunca recalcula). Con caseId,
   * la del caso; sin caseId, la más reciente del médico en cualquier caso
   * (resumen general, ej. dashboard).
   */
  recommendations(doctorId: string, caseId?: string): Promise<RecommendationsResponse> {
    const params: Record<string, string> = { doctorId };
    if (caseId) params['caseId'] = caseId;
    return this.api.get<RecommendationsResponse>('/api/matching/lawyers', params);
  }

  /** H-02: generación idempotente -- misma clave + mismo caso reutiliza el resultado; clave nueva = regenerar. */
  generateRecommendations(body: GenerateRecommendationsBody): Promise<RecommendationRunDto> {
    return this.api.post<RecommendationRunDto>('/api/matching/lawyers', body);
  }

  /** H-02: historial de ejecuciones de un caso -- lectura pura. */
  recommendationRuns(caseId: string): Promise<RecommendationRunSummaryDto[]> {
    return this.api.get<RecommendationRunSummaryDto[]>('/api/matching/recommendation-runs', { caseId });
  }

  /** H-02: detalle de una ejecución guardada -- lectura pura. */
  recommendationRun(runId: string): Promise<RecommendationRunDto> {
    return this.api.get<RecommendationRunDto>(`/api/matching/recommendation-runs/${runId}`);
  }

  contactRequests(params?: ContactRequestListParams): Promise<ContactRequestResponse[]> {
    return this.api.get<ContactRequestResponse[]>('/api/matching/contact-requests', params);
  }

  createContactRequest(body: CreateContactRequestBody): Promise<ContactRequestResponse> {
    return this.api.post<ContactRequestResponse>('/api/matching/contact-requests', body);
  }

  respondContactRequest(body: RespondContactRequestBody): Promise<ContactRequestResponse> {
    return this.api.patch<ContactRequestResponse>('/api/matching/contact-requests', body);
  }

  /** El médico cancela una solicitud propia aún pendiente. */
  cancelContactRequest(id: string): Promise<ContactRequestResponse> {
    return this.api.delete<ContactRequestResponse>(`/api/matching/contact-requests/${id}`);
  }

  relevantCases(lawyerId?: string): Promise<Record<string, unknown>> {
    return this.api.get<Record<string, unknown>>('/api/matching/relevant-cases', { lawyerId });
  }
}
