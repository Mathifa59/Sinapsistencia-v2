import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { ListResponseAuditLogResponse } from './generated/model/listResponseAuditLogResponse';

export interface AuditListParams {
  action?: string;
  userId?: string;
  resource?: string;
  search?: string;
  page?: number;
  pageSize?: number;
  [key: string]: string | number | undefined;
}

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly api = inject(ApiService);

  list(params?: AuditListParams): Promise<ListResponseAuditLogResponse> {
    return this.api.get<ListResponseAuditLogResponse>('/api/audit', params);
  }
}
