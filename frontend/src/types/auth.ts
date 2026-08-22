export type UserRole = 'CANDIDATE' | 'ADMIN';

export interface CurrentUser {
  candidateId: string;
  email: string;
  role: UserRole;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
}
