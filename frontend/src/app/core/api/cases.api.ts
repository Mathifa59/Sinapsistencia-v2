import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { CaseResponse } from './generated/model/caseResponse';
import type { CreateCaseRequest } from './generated/model/createCaseRequest';
import type { UpdateCaseRequest } from './generated/model/updateCaseRequest';
import type { ListResponseCaseResponse } from './generated/model/listResponseCaseResponse';

export interface CaseListParams {
  status?: string;
  priority?: string;
  doctorId?: string;
  search?: string;
  page?: number;
  pageSize?: number;
  [key: string]: string | number | undefined;
}

@Injectable({ providedIn: 'root' })
export class CasesApi {
  private readonly api = inject(ApiService);

  list(params?: CaseListParams): Promise<ListResponseCaseResponse> {
    return this.api.get<ListResponseCaseResponse>('/api/legal-cases', params);
  }

  get(id: string): Promise<CaseResponse> {
    return this.api.get<CaseResponse>(`/api/legal-cases/${id}`);
  }

  create(body: CreateCaseRequest): Promise<CaseResponse> {
    return this.api.post<CaseResponse>('/api/legal-cases', body);
  }

  update(id: string, body: UpdateCaseRequest): Promise<CaseResponse> {
    return this.api.put<CaseResponse>(`/api/legal-cases/${id}`, body);
  }
}
