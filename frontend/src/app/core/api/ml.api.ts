import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class MlApi {
  private readonly api = inject(ApiService);

  health(): Promise<Record<string, unknown>> {
    return this.api.get<Record<string, unknown>>('/api/ml/health');
  }

  risk(body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.api.post<Record<string, unknown>>('/api/ml/risk', body);
  }

  metrics(): Promise<ModelMetricDto[]> {
    return this.api.get<ModelMetricDto[]>('/api/ml/metrics');
  }
}

export interface ModelMetricDto {
  id?: string;
  modelName?: string;
  modelVersion?: string;
  precisionScore?: number;
  recallScore?: number;
  f1Score?: number;
  matchingRelevanceRate?: number;
  avgResponseTimeMs?: number;
  datasetSize?: number;
  notes?: string;
  evaluatedAt?: string;
}
