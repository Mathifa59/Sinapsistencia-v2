import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import type { UserDto } from './generated/model/userDto';
import type { CreateUserRequest } from './generated/model/createUserRequest';
import type { UpdateUserRequest } from './generated/model/updateUserRequest';

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly api = inject(ApiService);

  list(role?: string): Promise<UserDto[]> {
    return this.api.get<UserDto[]>('/api/users', { role });
  }

  get(id: string): Promise<UserDto> {
    return this.api.get<UserDto>(`/api/users/${id}`);
  }

  create(body: CreateUserRequest): Promise<UserDto> {
    return this.api.post<UserDto>('/api/users', body);
  }

  update(id: string, body: UpdateUserRequest): Promise<UserDto> {
    return this.api.put<UserDto>(`/api/users/${id}`, body);
  }

  toggleActive(id: string): Promise<UserDto> {
    return this.api.patch<UserDto>(`/api/users/${id}`);
  }
}
