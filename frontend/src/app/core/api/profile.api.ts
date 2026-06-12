import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { ProfileResponse } from './generated/model/profileResponse';
import type { UpdateProfileRequest } from './generated/model/updateProfileRequest';
import type { MessageResponse } from './generated/model/messageResponse';

@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly api = inject(ApiService);

  get(userId?: string): Promise<ProfileResponse> {
    return this.api.get<ProfileResponse>('/api/profile', { userId });
  }

  put(body: UpdateProfileRequest): Promise<MessageResponse> {
    return this.api.put<MessageResponse>('/api/profile', body);
  }

  patch(body: UpdateProfileRequest): Promise<MessageResponse> {
    return this.api.patch<MessageResponse>('/api/profile', body);
  }

  uploadAvatar(form: FormData): Promise<Record<string, string>> {
    return this.api.postForm<Record<string, string>>('/api/profile/avatar', form);
  }
}
