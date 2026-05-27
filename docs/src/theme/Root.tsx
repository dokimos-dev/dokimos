import React from "react";
import { Velm } from "@velmhq/embed-react";

export default function Root({ children }: { children: React.ReactNode }) {
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
