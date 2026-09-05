import { vi } from "vitest";
import type { ApiClient } from "../api/client";

export type Query = Record<string, unknown>;

const FAILURE = Symbol("failure");

type Failure = { [FAILURE]: true; status: number; code: string };

/** An answer for one path: a body, a body computed from the query, or a failure. */
export type Answer = unknown | Failure | ((query: Query) => unknown | Failure);

/** What the stubbed API says for each path. A path not listed answers 404. */
export type Routes = Record<string, Answer>;

/** The API refusing, with the status and stable code it would really send. */
export function failure(status: number, code: string): Failure {
  return { [FAILURE]: true, status, code };
}

const isFailure = (value: unknown): value is Failure =>
  typeof value === "object" && value !== null && FAILURE in value;

/**
 * A client whose every endpoint is stubbed by path, so a test states what the API
 * returned for each thing a screen asks and nothing is answered by accident. Paths
 * the test did not mention are a 404, which is also what the real API says.
 */
export function stubClient(routes: Routes) {
  const get = vi.fn(async (path: string, options?: { params?: { query?: Query } }) => {
    const query = options?.params?.query ?? {};
    if (!(path in routes)) {
      return { data: undefined, error: { error: "NOT_FOUND", message: "unstubbed" }, response: { status: 404 } };
    }
    const answer = routes[path];
    const resolved = typeof answer === "function" ? (answer as (q: Query) => unknown)(query) : answer;
    if (isFailure(resolved)) {
      return { data: undefined, error: { error: resolved.code, message: resolved.code }, response: { status: resolved.status } };
    }
    return { data: resolved, error: undefined, response: { status: 200 } };
  });
  return { client: { GET: get } as unknown as ApiClient, get };
}

/** The query objects sent to one path, in order. */
export function queriesTo(get: ReturnType<typeof stubClient>["get"], path: string): Query[] {
  return get.mock.calls
    .filter((call) => call[0] === path)
    .map((call) => (call[1] as { params?: { query?: Query } } | undefined)?.params?.query ?? {});
}
