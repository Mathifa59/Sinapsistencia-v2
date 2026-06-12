import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { DoctorCardDto } from './generated/model/doctorCardDto';
import type { LawyerCardDto } from './generated/model/lawyerCardDto';
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

@Injectable({ providedIn: 'root' })
export class MatchingApi {
  private readonly api = inject(ApiService);

  doctors(): Promise<DoctorCardDto[]> {
    return this.api.get<DoctorCardDto[]>('/api/matching/doctors');
  }

  lawyers(): Promise<LawyerCardDto[]> {
    return this.api.get<LawyerCardDto[]>('/api/matching/lawyers');
  }

  /** Con doctorId, /api/matching/lawyers responde recomendaciones ML (legacy). */
  recommendations(doctorId: string): Promise<RecommendationsResponse> {
    return this.api.get<RecommendationsResponse>('/api/matching/lawyers', { doctorId });
  }

  generateRecommendations(body: GenerateRecommendationsBody): Promise<RecommendationsResponse> {
    return this.api.post<RecommendationsResponse>('/api/matching/lawyers', body);
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

  relevantCases(lawyerId?: string): Promise<Record<string, unknown>> {
    return this.api.get<Record<string, unknown>>('/api/matching/relevant-cases', { lawyerId });
  }
}
