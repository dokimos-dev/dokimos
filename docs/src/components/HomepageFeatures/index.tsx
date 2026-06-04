import type { ReactNode } from "react";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
  title: string;
  glyph: string;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: "Dataset-driven evaluation",
    glyph: "▦",
    description: (
      <>
        Load test cases from JSON or CSV, or build them in code. Run the same dataset across
        experiments and JUnit tests, and track quality as it changes.
      </>
    ),
  },
  {
    title: "Built-in and agent evaluators",
    glyph: "◆",
    description: (
      <>
        Hallucination, faithfulness, contextual relevance, and LLM-as-judge, plus tool-call
        validity, trajectory, and task completion for agents.
      </>
    ),
  },
  {
    title: "Framework agnostic",
    glyph: "⌘",
    description: (
      <>
        The core depends on no AI framework, so it works with any LLM client. Optional one-line
        integrations cover Spring AI, LangChain4j, Koog, and JUnit.
      </>
    ),
  },
];

function Feature({ title, glyph, description }: FeatureItem) {
  return (
    <div className={styles.card}>
      <span className={styles.glyph} aria-hidden="true">
        {glyph}
      </span>
      <Heading as="h3" className={styles.cardTitle}>
        {title}
      </Heading>
      <p className={styles.cardText}>{description}</p>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className={styles.grid}>
        {FeatureList.map((props) => (
          <Feature key={props.title} {...props} />
        ))}
      </div>
    </section>
  );
}
