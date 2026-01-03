import type { ReactNode } from "react";
import clsx from "clsx";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
  title: string;
  Svg: React.ComponentType<React.ComponentProps<"svg">>;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: "Dataset-Driven Evaluation",
    Svg: require("@site/static/img/dokimos-dataset-driven-evals.svg").default,
    description: (
      <>
        Load test cases from JSON or CSV files, or create them programmatically.
        Run the same dataset across experiments or JUnit tests.
      </>
    ),
  },
  {
    title: "Built-in Evaluators",
    Svg: require("@site/static/img/built-in-evaluators.svg").default,
    description: <>Use built-in and LLM-based evaluators out of the box.</>,
  },
  {
    title: "Framework Integration",
    Svg: require("@site/static/img/framework-integration.svg").default,
    description: (
      <>
        Works with JUnit for parameterized testing and LangChain4j for
        evaluating AI Services. Integrate into existing CI/CD pipelines.
      </>
    ),
  },
];

function Feature({ title, Svg, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4")}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
