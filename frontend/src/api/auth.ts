import request from './request';
import type { AccessTokenResponse, CurrentUser } from '../types/auth';

const BASE_PATH = '/api/auth';

export const authApi = {
  register(email: string, password: string): Promise<CurrentUser> {
    return request.post<CurrentUser>(
      `${BASE_PATH}/register`,
      { email, password },
      { skipAuth: true },
    );
  },

  login(email: string, password: string): Promise<AccessTokenResponse> {
    return request.post<AccessTokenResponse>(
      `${BASE_PATH}/login`,
      { email, password },
      { skipAuth: true },
    );
  },

  me(): Promise<CurrentUser> {
    return request.get<CurrentUser>(`${BASE_PATH}/me`);
  },
};
