import React, { useEffect } from "react";
import { Velm } from "@velmhq/embed-react";

export default function Root({ children }: { children: React.ReactNode }) {
  // Release Cmd/Ctrl+K from the Velm widget so docs search owns it.
  useEffect(() => {
    document.getElementById("velm-embed-launcher")?.setAttribute("data-hotkey", "off");
  }, []);

  return (
    <>
      {children}
      <Velm
        agent="velm/support-dokimos-dev"
        mode="editorial"
        theme="auto"
        color="#000000"
        title="Dokimos Support"
        greeting="Hi! Ask me anything about Dokimos."
        suggested={[
          "How do I write my first evaluation?",
          "Which built-in evaluators are available?",
          "How do I run evals in CI?",
        ]}
      />
    </>
  );
}
