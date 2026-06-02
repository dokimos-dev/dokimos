import type { ReactNode } from "react";
import Footer from "@theme-original/DocItem/Footer";
import type FooterType from "@theme/DocItem/Footer";
import type { WrapperProps } from "@docusaurus/types";
import { useDoc } from "@docusaurus/plugin-content-docs/client";

import styles from "./styles.module.css";

type Props = WrapperProps<typeof FooterType>;

export default function FooterWrapper(props: Props): ReactNode {
  const { metadata } = useDoc();

  return (
    <>
      <div className={styles.actions}>
        <span className={styles.label}>For AI agents</span>
        <a className={styles.action} href={`/docs${metadata.permalink}.md`}>
          View as Markdown
        </a>
      </div>
      <Footer {...props} />
    </>
  );
}
