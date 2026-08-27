import { HealthCheck } from "./health-check";
import styles from "./page.module.css";

export default function Home() {
  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <header>
          <h1 className={styles.title}>AfnaiHisab</h1>
          <p className={styles.muted}>
            Phase 0 scaffold — shared expenses now, general-purpose accounting later.
          </p>
        </header>

        <HealthCheck />

        <footer className={styles.muted}>
          Next steps live in <code>docs/PLAN.md</code> (Phase 1 — web MVP).
        </footer>
      </main>
    </div>
  );
}
