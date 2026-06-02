import { useState, type ReactNode } from "react";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";

import styles from "./styles.module.css";

/**
 * A copy-paste prompt for a coding agent (Cursor, Claude Code, and similar). A developer
 * pastes it into their agent to get a first Dokimos eval written against their own code,
 * without reading the docs first. The dependency version tracks the configured release.
 */
export default function AgentPrompt(): ReactNode {
  const { siteConfig } = useDocusaurusContext();
  const version = (siteConfig.customFields?.dokimosVersion as string) ?? "latest";
  const [copied, setCopied] = useState(false);

  const prompt = `I want to evaluate my LLM or AI-agent code with Dokimos, the LLM evaluation framework for Java and Kotlin.

Read https://dokimos.dev/llms-full.txt for the full API, then:
1. Add the dev.dokimos:dokimos-junit test dependency (version ${version}) to my build.
2. Look at what my app does (RAG, chatbot, or a tool-using agent) and pick the right evaluators.
3. Write a JUnit test that runs my code and asserts quality with Dokimos, using @DatasetSource if I have a dataset.
4. For a tool-using agent, capture the run as an AgentTrace and check the tool calls.

Keep it to one working test I can run with my existing build, and tell me how to run it.`;

  const copy = () => {
    void navigator.clipboard.writeText(prompt).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className={styles.wrap}>
      <div className={styles.header}>
        <span className={styles.label}>Start with a coding agent</span>
        <button className={styles.copy} onClick={copy} type="button">
          {copied ? "Copied" : "Copy prompt"}
        </button>
      </div>
      <pre className={styles.body}>{prompt}</pre>
    </div>
  );
}
