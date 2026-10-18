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

  it('preserves pagination metadata when envelope contains pagination', async () => {
    const mockJobs = [{ id: 1, title: 'Junior Dev' }];
    const mockPagination = { page: 0, size: 10, totalElements: 1, totalPages: 1 };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        success: true,
        data: mockJobs,
        pagination: mockPagination,
        message: null,
      }),
    } as Response);

    const result = await apiClient.get<{ data: typeof mockJobs; pagination: typeof mockPagination }>('/jobs');
    expect(result).toMatchObject({
      data: mockJobs,
      pagination: mockPagination,
    });
  });

  it('serializes query parameters properly, omitting empty and undefined values', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ success: true, data: [], message: null }),
    } as Response);

    await apiClient.get('/jobs', {
      params: {
        page: 0,
        size: 9,
        employmentType: 'INTERNSHIP',
        workMode: 'ONSITE',
        emptyFilter: '',
        nullFilter: null,
        undefinedFilter: undefined,
      },
    });

    expect(fetchSpy).toHaveBeenCalled();
    const requestUrl = fetchSpy.mock.calls[0][0] as string;
    expect(requestUrl).toContain('page=0');
    expect(requestUrl).toContain('size=9');
    expect(requestUrl).toContain('employmentType=INTERNSHIP');
    expect(requestUrl).toContain('workMode=ONSITE');
    expect(requestUrl).not.toContain('emptyFilter');
    expect(requestUrl).not.toContain('nullFilter');
    expect(requestUrl).not.toContain('undefinedFilter');
  });

  it('correctly captures 400 Bad Request error from backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 400,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        success: false,
        data: null,
        message: 'Parameter "workMode" should be of type WorkMode',
        errorCode: 'VALIDATION_FAILED',
      }),
    } as Response);

    try {
      await apiClient.get('/jobs?workMode=INVALID');
      expect.fail('Should have thrown ApiClientError');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiClientError);
      const apiErr = err as ApiClientError;
      expect(apiErr.status).toBe(400);
      expect(apiErr.errorCode).toBe('VALIDATION_FAILED');
      expect(apiErr.message).toContain('workMode');
    }
  });

  it('correctly captures 404 Not Found error from backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 404,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        success: false,
        data: null,
        message: 'Job not found with id 9999',
        errorCode: 'NOT_FOUND',
      }),
    } as Response);

    try {
      await apiClient.get('/jobs/9999');
      expect.fail('Should have thrown ApiClientError');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiClientError);
      const apiErr = err as ApiClientError;
      expect(apiErr.status).toBe(404);
      expect(apiErr.message).toBe('Job not found with id 9999');
    }
  });

  it('correctly handles 500 Internal Server Error without exposing stack traces', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 500,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        success: false,
        data: null,
        message: 'An unexpected error occurred.',
        errorCode: 'INTERNAL_ERROR',
      }),
    } as Response);

    try {
      await apiClient.get('/jobs');
      expect.fail('Should have thrown ApiClientError');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiClientError);
      const apiErr = err as ApiClientError;
      expect(apiErr.status).toBe(500);
      expect(apiErr.message).toBe('An unexpected error occurred.');
    }
  });
});
