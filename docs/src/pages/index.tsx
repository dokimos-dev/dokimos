import type { ReactNode } from "react";
import Link from "@docusaurus/Link";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import Layout from "@theme/Layout";
import { Highlight, Prism, themes, type PrismTheme } from "prism-react-renderer";
import HomepageFeatures from "@site/src/components/HomepageFeatures";
import Heading from "@theme/Heading";

import styles from "./index.module.css";

// Java isn't in prism-react-renderer's default language bundle. Register it on
// the renderer's own Prism instance so the hero snippet tokenizes correctly.
(globalThis as { Prism?: typeof Prism }).Prism = Prism;
// eslint-disable-next-line @typescript-eslint/no-require-imports
require("prismjs/components/prism-java");

// Same One Dark theme the docs code blocks use, with a transparent surface so
// the hero panel background shows through.
const heroTheme: PrismTheme = {
  ...themes.oneDark,
  plain: { ...themes.oneDark.plain, backgroundColor: "transparent" },
};

const HERO_CODE = `@Test
void answersStayGrounded() {
    var experiment = Experiment.builder()
        .name("rag-eval")
        .dataset(Dataset.fromJson("qa-golden.json"))
        .task(example -> ragPipeline.answer(example.input()))
        .evaluators(List.of(
            new CorrectnessEvaluator(),
            new FaithfulnessEvaluator(judge)))
        .build();

    var result = experiment.run();
    assertThat(result.passRate()).isGreaterThan(0.9);
}`;

function HomepageHeader() {
  return (
    <header className={styles.hero}>
      <div className={styles.heroInner}>
        <div className={styles.heroCopy}>
          <span className={styles.eyebrow}>LLM evaluation for the JVM</span>
          <Heading as="h1" className={styles.heroTitle}>
            Test, track, and trust your LLM apps in Java and Kotlin.
          </Heading>
          <p className={styles.heroSubtitle}>
            Evaluate responses, track quality over time, and catch regressions before they reach
            production. Integrates with JUnit, LangChain4j, and Spring AI so evaluations run in your
            existing test suite and CI.
          </p>
          <div className={styles.heroButtons}>
            <Link className="button button--primary button--lg" to="/overview">
              Get started
            </Link>
            <Link
              className="button button--secondary button--lg"
              href="https://github.com/dokimos-dev/dokimos"
            >
              View on GitHub ↗
            </Link>
          </div>
        </div>
        <div className={styles.heroCode}>
          <div className={styles.codeChrome}>
            <span className={styles.codeDot} />
            <span className={styles.codeDot} />
            <span className={styles.codeDot} />
            <span className={styles.codeName}>RagEvalTest.java</span>
          </div>
          <Highlight theme={heroTheme} code={HERO_CODE} language="java">
            {({ tokens, getLineProps, getTokenProps }) => (
              <pre className={styles.codeBody}>
                <code>
                  {tokens.map((line, i) => (
                    <span key={i} {...getLineProps({ line })} style={{ display: "block" }}>
                      {line.map((token, key) => (
                        <span key={key} {...getTokenProps({ token })} />
                      ))}
                    </span>
                  ))}
                </code>
              </pre>
            )}
          </Highlight>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Evaluate LLM responses, track quality over time, and catch regressions before they reach production. Integrates with JUnit, LangChain4j, and Spring AI."
    >
      <HomepageHeader />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
