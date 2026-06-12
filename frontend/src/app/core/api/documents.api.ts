import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { DocumentResponse } from './generated/model/documentResponse';
import type { CreateDocumentRequest } from './generated/model/createDocumentRequest';
import type { UpdateDocumentRequest } from './generated/model/updateDocumentRequest';
import type { ListResponseDocumentResponse } from './generated/model/listResponseDocumentResponse';

export interface DocumentListParams {
  status?: string;
  type?: string;
  authorId?: string;
  search?: string;
  page?: number;
  pageSize?: number;
  [key: string]: string | number | undefined;
}

@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly api = inject(ApiService);

  list(params?: DocumentListParams): Promise<ListResponseDocumentResponse> {
    return this.api.get<ListResponseDocumentResponse>('/api/documents', params);
  }

  get(id: string): Promise<DocumentResponse> {
    return this.api.get<DocumentResponse>(`/api/documents/${id}`);
  }

  create(body: CreateDocumentRequest): Promise<DocumentResponse> {
    return this.api.post<DocumentResponse>('/api/documents', body);
  }

  update(id: string, body: UpdateDocumentRequest): Promise<DocumentResponse> {
    return this.api.put<DocumentResponse>(`/api/documents/${id}`, body);
  }
}
