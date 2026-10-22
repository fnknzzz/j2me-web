import type {
  NetworkHttpRequest,
  NetworkHttpResponse,
  NetworkSocket,
} from '@jsjvm/j2me-simulator/env';

export interface HttpRequestInfo {
  method: string;
  url: string;
  headers?: Record<string, string>;
  body?: Uint8Array;
}

export interface HttpResponseData {
  body: Uint8Array;
  headers?: Record<string, string>;
}

export interface NetworkInterceptor {
  httpRequest?: (
    request: HttpRequestInfo,
  ) => Promise<HttpResponseData | null> | HttpResponseData | null;

  openSocket?: (
    host: string,
    port: number,
  ) => Promise<NetworkSocket> | NetworkSocket | null;

  isNetworkAvailable?: () => boolean;
}

export interface NetworkHooksOptions {
  interceptor?: NetworkInterceptor;
  allowRealNetwork?: boolean;
}

export const createNetworkHooks = (options?: NetworkHooksOptions) => {
  const interceptor = options?.interceptor;
  const allowRealNetwork = options?.allowRealNetwork ?? false;

  const defaultHttpRequest = async (
    request: NetworkHttpRequest,
  ): Promise<NetworkHttpResponse> => {
    if (!allowRealNetwork) {
      throw new Error(
        `Network request blocked: ${request.method} ${request.url}. ` +
          'Set allowRealNetwork: true or provide a custom interceptor.',
      );
    }

    const controller = new AbortController();
    const timeoutId = request.timeout
      ? setTimeout(() => controller.abort(), request.timeout)
      : undefined;

    try {
      const response = await fetch(request.url, {
        method: request.method,
        headers: request.headers,
        body: request.body as BodyInit | null | undefined,
        signal: controller.signal,
      });

      const body = new Uint8Array(await response.arrayBuffer());
      const headers: Record<string, string> = {};
      response.headers.forEach((value, key) => {
        headers[key] = value;
      });

      return {
        status: response.status,
        statusText: response.statusText,
        headers,
        body,
      };
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    }
  };

  const defaultOpenSocket = async (
    host: string,
    port: number,
  ): Promise<NetworkSocket> => {
    throw new Error(
      `Socket connection blocked: ${host}:${port}. ` +
        'WebSocket is not supported in browser environment. ' +
        'Provide a custom interceptor to handle socket connections.',
    );
  };

  return {
    network: {
      httpRequest: async (
        request: NetworkHttpRequest,
      ): Promise<NetworkHttpResponse> => {
        if (interceptor?.httpRequest) {
          const requestInfo: HttpRequestInfo = {
            method: request.method,
            url: request.url,
            headers: request.headers,
            body: request.body,
          };

          const result = await interceptor.httpRequest(requestInfo);
          if (result) {
            return {
              status: 200,
              statusText: 'OK',
              headers: result.headers || {},
              body: result.body,
            };
          }
        }

        return defaultHttpRequest(request);
      },

      openSocket: async (
        host: string,
        port: number,
      ): Promise<NetworkSocket> => {
        if (interceptor?.openSocket) {
          const result = await interceptor.openSocket(host, port);
          if (result) {
            return result;
          }
        }

        return defaultOpenSocket(host, port);
      },

      isNetworkAvailable: (): boolean => {
        if (interceptor?.isNetworkAvailable) {
          return interceptor.isNetworkAvailable();
        }

        return typeof navigator !== 'undefined' && navigator.onLine;
      },
    },
  };
};
