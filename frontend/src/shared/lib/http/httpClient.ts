type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

interface HttpRequestOptions extends Omit<RequestInit, 'body' | 'method'> {
  method?: HttpMethod
  data?: unknown
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const DEFAULT_API_TIMEOUT_MS = 10_000
const configuredTimeout = Number(import.meta.env.VITE_API_TIMEOUT_MS)
const API_TIMEOUT_MS =
  Number.isFinite(configuredTimeout) && configuredTimeout > 0
    ? configuredTimeout
    : DEFAULT_API_TIMEOUT_MS

function createRequestSignal(timeoutMs: number, externalSignal?: AbortSignal) {
  const controller = new AbortController()
  let timedOut = false

  const timeoutId = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)

  const onExternalAbort = () => {
    controller.abort()
  }

  if (externalSignal) {
    if (externalSignal.aborted) {
      controller.abort()
    } else {
      externalSignal.addEventListener('abort', onExternalAbort, { once: true })
    }
  }

  const cleanup = () => {
    clearTimeout(timeoutId)
    if (externalSignal) {
      externalSignal.removeEventListener('abort', onExternalAbort)
    }
  }

  return {
    signal: controller.signal,
    didTimeout: () => timedOut,
    cleanup,
  }
}

export async function httpRequest<T>(
  path: string,
  options: HttpRequestOptions = {},
): Promise<T> {
  const { data, headers, method = 'GET', signal: externalSignal, ...rest } = options
  const { signal, didTimeout, cleanup } = createRequestSignal(
    API_TIMEOUT_MS,
    externalSignal ?? undefined,
  )

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: data ? JSON.stringify(data) : undefined,
      signal,
      ...rest,
    })
  } catch (error) {
    if (didTimeout()) {
      throw new Error(`요청 시간이 초과되었습니다. (${API_TIMEOUT_MS}ms)`)
    }
    throw error
  } finally {
    cleanup()
  }

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    return (await response.json()) as T
  }

  return (await response.text()) as T
}
