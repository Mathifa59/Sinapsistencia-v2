import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { CaseResponse } from './generated/model/caseResponse';
import type { CreateCaseRequest } from './generated/model/createCaseRequest';
import type { UpdateCaseRequest } from './generated/model/updateCaseRequest';
import type { ListResponseCaseResponse } from './generated/model/listResponseCaseResponse';
import type { ContextPayload } from './generated/model/contextPayload';
import type { RecommendationsResponse } from './generated/model/recommendationsResponse';

export interface CaseListParams {
  status?: string;
  priority?: string;
  doctorId?: string;
  lawyerId?: string;
  search?: string;
  page?: number;
  pageSize?: number;
  assignedOnly?: boolean;
  [key: string]: string | number | boolean | undefined;
}

export interface MlClassificationDto {
  id?: string;
  caseType?: string;
  urgency?: string;
  complexity?: string;
  suggestedSpecialty?: string;
  confidence?: number;
  modelVersion?: string;
  responseTimeMs?: number;
  createdAt?: string;
}

export interface LegalResponseDto {
  id?: string;
  content?: string;
  recommendations?: string;
  observations?: string;
  isReviewed?: boolean;
  lawyerName?: string;
  createdAt?: string;
}

export interface CaseEventDto {
  id?: string;
  eventDate?: string;
  eventType?: string;
  description?: string;
  createdByName?: string;
  createdAt?: string;
}

export interface TimelineEntryDto {
  id?: string;
  kind?: string;
  title?: string;
  description?: string;
  occurredAt?: string;
  actorName?: string | null;
}

/** Campos del RF persistido (V11) aún no regenerados en el cliente OpenAPI. */
export type MlClassificationExtended = MlClassificationDto & {
  riskScore?: number | null;
  riskLevel?: string | null;
  riskFactorsJson?: string | null;
};

export interface CaseDetailDto {
  caseData: CaseResponse;
  classification?: MlClassificationExtended | null;
  responses?: LegalResponseDto[];
  events?: CaseEventDto[];
  timeline?: TimelineEntryDto[];
  recommendations?: RecommendationsResponse | null;
  advisoryNote?: string;
}

export interface CaseReportDto {
  caseData: CaseResponse;
  classification?: MlClassificationDto | null;
  responses?: LegalResponseDto[];
  timeline?: TimelineEntryDto[];
  documentTitles?: string[];
  advisoryNote?: string;
  generatedAt?: string;
}

export interface EditCaseBody {
  title?: string;
  description?: string;
  priority?: string;
  medicalSpecialty?: string;
  eventType?: string;
  perceivedUrgency?: string;
  notes?: string;
  context?: ContextPayload;
}

export interface CreateCaseEventBody {
  eventDate: string;
  eventType: string;
  description?: string;
}

export interface CreateLegalResponseBody {
  content: string;
  recommendations?: string;
  observations?: string;
}

@Injectable({ providedIn: 'root' })
export class CasesApi {
  private readonly api = inject(ApiService);

  list(params?: CaseListParams): Promise<ListResponseCaseResponse> {
    const query: Record<string, string | number | undefined> = {};
    if (params) {
      for (const [key, value] of Object.entries(params)) {
        if (value === undefined) continue;
        query[key] = typeof value === 'boolean' ? String(value) : value;
      }
    }
    return this.api.get<ListResponseCaseResponse>('/api/legal-cases', query);
  }

  get(id: string): Promise<CaseResponse> {
    return this.api.get<CaseResponse>(`/api/legal-cases/${id}`);
  }

  getDetail(id: string): Promise<CaseDetailDto> {
    return this.api.get<CaseDetailDto>(`/api/legal-cases/${id}/detail`);
  }

  getReport(id: string): Promise<CaseReportDto> {
    return this.api.get<CaseReportDto>(`/api/legal-cases/${id}/report`);
  }

  create(body: CreateCaseRequest): Promise<CaseResponse> {
    return this.api.post<CaseResponse>('/api/legal-cases', body);
  }

  update(id: string, body: UpdateCaseRequest): Promise<CaseResponse> {
    return this.api.put<CaseResponse>(`/api/legal-cases/${id}`, body);
  }

  edit(id: string, body: EditCaseBody): Promise<CaseResponse> {
    return this.api.put<CaseResponse>(`/api/legal-cases/${id}/edit`, body);
  }

  close(id: string, reason: string): Promise<CaseResponse> {
    return this.api.post<CaseResponse>(`/api/legal-cases/${id}/close`, { reason });
  }

  startReview(id: string): Promise<CaseResponse> {
    return this.api.post<CaseResponse>(`/api/legal-cases/${id}/start-review`, {});
  }

  addEvent(id: string, body: CreateCaseEventBody): Promise<CaseEventDto> {
    return this.api.post<CaseEventDto>(`/api/legal-cases/${id}/events`, body);
  }

  addResponse(id: string, body: CreateLegalResponseBody): Promise<LegalResponseDto> {
    return this.api.post<LegalResponseDto>(`/api/legal-cases/${id}/responses`, body);
  }

  reviewResponse(caseId: string, responseId: string): Promise<LegalResponseDto> {
    return this.api.patch<LegalResponseDto>(`/api/legal-cases/${caseId}/responses/${responseId}/review`, {});
  }
}
