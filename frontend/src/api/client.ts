import type { ApiResponse, ApiErrorResponse } from '../types/api';

/**
 * Normalized API Client Error
 */
export class ApiClientError extends Error {
  public readonly status: number;
  public readonly errorCode: string;
  public readonly details?: unknown;

  constructor(status: number, message: string, errorCode: string = 'UNKNOWN_ERROR', details?: unknown) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
  }
}

/**
 * In-memory token holder
 * STRICT SECURITY RULE: Never persist in localStorage, sessionStorage, or cookies.
 */
let inMemoryToken: string | null = null;

export const setAuthToken = (token: string | null): void => {
  inMemoryToken = token;
};

export const getAuthToken = (): string | null => {
  return inMemoryToken;
};

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  params?: Record<string, string | number | boolean | undefined | null>;
}

/**
 * Core HTTP Request Handler
 */
async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { body, params, headers: customHeaders, ...customConfig } = options;

  let url = endpoint.startsWith('http') ? endpoint : `/api${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  if (params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        searchParams.append(key, String(value));
      }
    });
    const queryString = searchParams.toString();
    if (queryString) {
      url += (url.includes('?') ? '&' : '?') + queryString;
    }
  }

  const headers = new Headers();

  // Set default JSON Content-Type unless FormData is passed
  const isFormData = body instanceof FormData;
  if (!isFormData) {
    headers.set('Content-Type', 'application/json');
    headers.set('Accept', 'application/json');
  }

  // Inject in-memory JWT authorization token if available
  const token = getAuthToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  // Apply any custom headers
  if (customHeaders) {
    new Headers(customHeaders).forEach((value, key) => {
      headers.set(key, value);
    });
  }

  const config: RequestInit = {
    ...customConfig,
    headers,
    body: isFormData ? (body as FormData) : body !== undefined ? JSON.stringify(body) : undefined,
  };

  let response: Response;
  try {
    response = await fetch(url, config);
  } catch (networkError) {
    throw new ApiClientError(0, 'Network connection error or backend service unreachable', 'NETWORK_ERROR', networkError);
  }

  // Handle binary responses (e.g., PDF resumes)
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/pdf') || contentType.includes('application/octet-stream')) {
    if (!response.ok) {
      throw new ApiClientError(response.status, 'Failed to download binary file', 'DOWNLOAD_ERROR');
    }
    return (await response.blob()) as unknown as T;
  }

  // Handle No Content (204)
  if (response.status === 204) {
    return null as unknown as T;
  }

  let jsonResult: ApiResponse<T> | ApiErrorResponse | null = null;
  try {
    jsonResult = await response.json();
  } catch {
    if (!response.ok) {
      throw new ApiClientError(response.status, response.statusText || 'An unexpected error occurred', 'HTTP_ERROR');
    }
    return null as unknown as T;
  }

  if (!response.ok || (jsonResult && 'success' in jsonResult && jsonResult.success === false)) {
    const errorPayload = jsonResult as ApiErrorResponse;
    const errorMessage = errorPayload?.message || response.statusText || 'Operation failed';
    const errorCode = errorPayload?.errorCode || `HTTP_${response.status}`;
    throw new ApiClientError(response.status, errorMessage, errorCode, errorPayload?.data);
  }

  // Return the unwrapped canonical payload, preserving pagination if present
  if (jsonResult && 'success' in jsonResult && jsonResult.success === true) {
    if ('pagination' in jsonResult && jsonResult.pagination) {
      return {
        data: jsonResult.data,
        pagination: jsonResult.pagination,
        content: jsonResult.data,
        page: {
          pageNumber: jsonResult.pagination.page,
          pageSize: jsonResult.pagination.size,
          totalElements: jsonResult.pagination.totalElements,
          totalPages: jsonResult.pagination.totalPages,
        },
      } as unknown as T;
    }
    return jsonResult.data as T;
  }

  return jsonResult as unknown as T;
}

export const apiClient = {
  get: <T>(endpoint: string, options?: Omit<RequestOptions, 'body' | 'method'>): Promise<T> => {
    return request<T>(endpoint, { ...options, method: 'GET' });
  },
  post: <T>(endpoint: string, body?: unknown, options?: Omit<RequestOptions, 'body' | 'method'>): Promise<T> => {
    return request<T>(endpoint, { ...options, method: 'POST', body });
  },
  put: <T>(endpoint: string, body?: unknown, options?: Omit<RequestOptions, 'body' | 'method'>): Promise<T> => {
    return request<T>(endpoint, { ...options, method: 'PUT', body });
  },
  patch: <T>(endpoint: string, body?: unknown, options?: Omit<RequestOptions, 'body' | 'method'>): Promise<T> => {
    return request<T>(endpoint, { ...options, method: 'PATCH', body });
  },
  delete: <T>(endpoint: string, options?: Omit<RequestOptions, 'body' | 'method'>): Promise<T> => {
    return request<T>(endpoint, { ...options, method: 'DELETE' });
  },
};
