import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { apiClient, setAuthToken, getAuthToken, ApiClientError } from '../client';

describe('API Client Foundation', () => {
  beforeEach(() => {
    setAuthToken(null);
    vi.restoreAllMocks();
  });

  afterEach(() => {
    setAuthToken(null);
  });

  it('manages in-memory token correctly without web storage persistence', () => {
    expect(getAuthToken()).toBeNull();
    setAuthToken('mock-jwt-token-123');
    expect(getAuthToken()).toBe('mock-jwt-token-123');
    setAuthToken(null);
    expect(getAuthToken()).toBeNull();
  });

  it('unwraps canonical success responses properly', async () => {
    const mockData = { id: 1, title: 'Junior Frontend Dev' };
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ success: true, data: mockData, message: null }),
    } as Response);

    const result = await apiClient.get<typeof mockData>('/jobs/1');
    expect(result).toEqual(mockData);
  });

  it('injects Bearer token in headers when token is set', async () => {
    setAuthToken('secret-test-token');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ success: true, data: { user: 'test' }, message: null }),
    } as Response);

    await apiClient.get('/auth/me');

    expect(fetchSpy).toHaveBeenCalled();
    const callArgs = fetchSpy.mock.calls[0];
    const headers = callArgs[1]?.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer secret-test-token');
  });

  it('normalizes canonical backend error envelopes into ApiClientError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 403,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        success: false,
        data: null,
        message: 'Account disabled',
        errorCode: 'FORBIDDEN',
      }),
    } as Response);

    await expect(apiClient.get('/candidate/profile')).rejects.toThrow(ApiClientError);

    try {
      await apiClient.get('/candidate/profile');
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        expect(err.status).toBe(403);
        expect(err.errorCode).toBe('FORBIDDEN');
        expect(err.message).toBe('Account disabled');
      }
    }
  });
});
