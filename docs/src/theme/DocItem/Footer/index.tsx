import type { ReactNode } from "react";
import Footer from "@theme-original/DocItem/Footer";
import type FooterType from "@theme/DocItem/Footer";
import type { WrapperProps } from "@docusaurus/types";
import { useDoc } from "@docusaurus/plugin-content-docs/client";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";

import styles from "./styles.module.css";

type Props = WrapperProps<typeof FooterType>;

export default function FooterWrapper(props: Props): ReactNode {
  const { metadata } = useDoc();
  const { siteConfig } = useDocusaurusContext();

  const mdUrl = `${siteConfig.url}/docs${metadata.permalink}.md`;
  const askPrompt = `Read ${mdUrl} and help me use this part of Dokimos.`;
  const chatGpt = `https://chatgpt.com/?q=${encodeURIComponent(askPrompt)}`;
  const claude = `https://claude.ai/new?q=${encodeURIComponent(askPrompt)}`;

  return (
    <>
      <div className={styles.actions}>
        <span className={styles.label}>For AI agents</span>
        <a className={styles.action} href={`/docs${metadata.permalink}.md`}>
          View as Markdown
        </a>
        <a className={styles.action} href={chatGpt} target="_blank" rel="noopener noreferrer">
          Open in ChatGPT
        </a>
        <a className={styles.action} href={claude} target="_blank" rel="noopener noreferrer">
          Open in Claude
        </a>
      </div>
      <Footer {...props} />
    </>
  );
}
