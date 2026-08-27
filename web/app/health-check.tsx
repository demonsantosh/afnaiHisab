"use client";

import { useEffect, useState } from "react";
import { API_V1, ApiError, fetchHealth, type HealthResponse } from "@/lib/api";
import styles from "./page.module.css";

type State =
  | { kind: "loading" }
  | { kind: "loaded"; report: HealthResponse }
  | { kind: "failed"; message: string };

/**
 * Deliberately a Client Component: the point of Phase 0's health check is to prove the
 * *browser* can reach the Ktor server cross-origin, which is what exercises ADR-0015's CORS
 * allow-list. Fetching this on the server would prove nothing about the browser's path.
 */
export function HealthCheck() {
  const [state, setState] = useState<State>({ kind: "loading" });
  // Bumping this re-runs the effect; "Check again" is a new attempt, not a re-render of the old
  // one. The effect never calls setState synchronously (react-hooks/set-state-in-effect) — only
  // from the fetch's callbacks, once the external system has actually answered.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    fetchHealth(controller.signal)
      .then((report) => setState({ kind: "loaded", report }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: "failed", message: describe(error) });
      });
    return () => controller.abort();
  }, [attempt]);

  const check = () => {
    setState({ kind: "loading" });
    setAttempt((previous) => previous + 1);
  };

  return (
    <section className={styles.card} aria-live="polite">
      <header className={styles.cardHeader}>
        <h2>Backend health</h2>
        <button
          type="button"
          className={styles.button}
          onClick={check}
          disabled={state.kind === "loading"}
        >
          {state.kind === "loading" ? "Checking…" : "Check again"}
        </button>
      </header>

      <p className={styles.endpoint}>
        <code>GET {API_V1}/health</code>
      </p>

      {state.kind === "loading" && <p className={styles.muted}>Contacting the server…</p>}

      {state.kind === "failed" && (
        <div className={styles.bad} role="alert">
          <p>
            <strong>Unreachable.</strong> {state.message}
          </p>
          <p className={styles.muted}>
            Start the backend with <code>./gradlew :server:run</code> from the repo root,
            then check again.
          </p>
        </div>
      )}

      {state.kind === "loaded" && (
        <div className={state.report.status === "OK" ? styles.good : styles.bad}>
          <p className={styles.status}>
            {state.report.status === "OK" ? "All systems up" : "Degraded"}
          </p>
          <dl className={styles.details}>
            <dt>Service</dt>
            <dd>{state.report.service}</dd>
            <dt>Version</dt>
            <dd>{state.report.version}</dd>
            <dt>Database</dt>
            <dd>{state.report.database}</dd>
            <dt>Checked at</dt>
            <dd>{state.report.checkedAt}</dd>
          </dl>
        </div>
      )}
    </section>
  );
}

function describe(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}: ${error.message}`;
  if (error instanceof TypeError) {
    // What a browser reports for a refused connection or a blocked (CORS) response — the two
    // failure modes worth distinguishing during Phase 0 setup.
    return "The request never completed. The server may be down, or the browser's origin may not be on the CORS allow-list.";
  }
  return error instanceof Error ? error.message : String(error);
}
