const ACCESS_TOKEN_KEY = 'accessToken';
export const AUTH_CHANGED_EVENT = 'auth:changed';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function storeAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function notifyAuthenticationChanged(): void {
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}
