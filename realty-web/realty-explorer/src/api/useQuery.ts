import { useEffect, useState } from "react";

/** What went wrong, as far as the API said. Every field is absent when it said nothing. */
export type ApiError = {
  httpStatus?: number;
  /** The API's stable code, e.g. `WORLD_NOT_FOUND`. */
  code?: string;
  message?: string;
};

export type QueryState<T> =
  | { status: "loading" }
  | { status: "error"; error: ApiError }
  | { status: "ready"; data: T };

/** The shape every openapi-fetch call resolves to; typed loosely so tests can stub it. */
type Outcome<T> = { data?: T; error?: unknown; response?: { status: number } };

/**
 * Runs one API call whenever `deps` change, and never lets a stale answer land.
 *
 * Filters change faster than requests complete, so without the cancellation a slow
 * response to the previous filter overwrites the fast one to the current filter.
 */
export function useQuery<T>(
  run: () => Promise<Outcome<T>>,
  deps: ReadonlyArray<unknown>,
): QueryState<T> {
  const [state, setState] = useState<QueryState<T>>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    setState({ status: "loading" });

    run().then(
      ({ data, error, response }) => {
        if (cancelled) return;
        if (error !== undefined || data === undefined) {
          setState({ status: "error", error: describe(error, response) });
          return;
        }
        setState({ status: "ready", data });
      },
      (thrown: unknown) => {
        if (cancelled) return;
        setState({
          status: "error",
          error: { message: thrown instanceof Error ? thrown.message : String(thrown) },
        });
      },
    );

    return () => {
      cancelled = true;
    };
  }, deps);

  return state;
}

function describe(error: unknown, response?: { status: number }): ApiError {
  const body = typeof error === "object" && error !== null ? (error as Record<string, unknown>) : {};
  return {
    httpStatus: response?.status,
    code: typeof body.error === "string" ? body.error : undefined,
    message: typeof body.message === "string" ? body.message : undefined,
  };
}
