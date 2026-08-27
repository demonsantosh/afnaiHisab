/**
 * The single place `web` knows how to talk to the Ktor backend.
 *
 * Everything goes through `/api/v1` (ADR-0015) and every failure is expected to arrive in the
 * standard envelope `{ "error": { "code", "message" } }` — the same shape Android and iOS will
 * parse later, which is why it is centralised here rather than inlined per component.
 */

/** Set `NEXT_PUBLIC_API_BASE_URL` in `web/.env.local` to point at a non-default backend. */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const API_V1 = `${API_BASE_URL}/api/v1`;

export type ComponentStatus = "UP" | "DOWN";
export type ServiceStatus = "OK" | "DEGRADED";

export interface HealthResponse {
  status: ServiceStatus;
  service: string;
  version: string;
  database: ComponentStatus;
  checkedAt: string;
}

export interface ApiErrorEnvelope {
  error: {
    code: string;
    message: string;
  };
}

/** Thrown for anything the backend reported in ADR-0015's error envelope. */
export class ApiError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

function isErrorEnvelope(body: unknown): body is ApiErrorEnvelope {
  if (typeof body !== "object" || body === null || !("error" in body)) {
    return false;
  }
  const { error } = body as { error: unknown };
  return (
    typeof error === "object" && error !== null && "code" in error && "message" in error
  );
}

/**
 * `GET /api/v1/health`.
 *
 * A 503 is not a transport failure: the server answers with a full report whose `status` is
 * `DEGRADED`, and the page renders that as a real (bad) state rather than as "could not reach the
 * server". Only an envelope or an unreadable response is treated as an error.
 */
export async function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const response = await fetch(`${API_V1}/health`, {
    signal,
    headers: { Accept: "application/json" },
    cache: "no-store",
  });

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new ApiError(
      "INVALID_RESPONSE",
      `The server answered ${response.status} with a body that is not JSON.`,
    );
  }

  if (isErrorEnvelope(body)) {
    throw new ApiError(body.error.code, body.error.message);
  }

  return body as HealthResponse;
}
