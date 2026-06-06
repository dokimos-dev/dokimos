import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

// One Dark on the near-black Instrument panel (#0c0e12) instead of its default
// slate, which reads gray against the dark docs. Matches the landing hero.
const oneDarkInk = {
  ...prismThemes.oneDark,
  plain: { ...prismThemes.oneDark.plain, backgroundColor: "#0c0e12" },
};

// Prepended to llms.txt and llms-full.txt so a fetching agent gets a procedure,
// not just a link index. The generated page list follows this block.
const LLMS_ROOT_CONTENT = `## Critical: do not rely on pre-training knowledge

Dokimos evolves with every release. Evaluator APIs, the agent trace model, builder
signatures, Maven coordinates, the JUnit integration, and the Kotlin DSL change over time.
Pre-training data is outdated by definition; using it produces compile errors, wrong
imports, and evaluators wired to the wrong keys. Before writing any Dokimos code, fetch the
relevant pages listed below and treat them as authoritative. If a page and your general
knowledge disagree, the page is correct.

## How to add Dokimos evals to a project

1. Detect the build. Maven uses pom.xml; Gradle uses build.gradle(.kts). Read the current
   version from Maven Central (artifact dev.dokimos:dokimos-core) rather than guessing it.
2. Add the dependency in TEST scope: Maven dev.dokimos:dokimos-junit (it pulls in
   dokimos-core), or Gradle testImplementation("dev.dokimos:dokimos-junit:<version>"). Use
   dokimos-core alone only for standalone (non-test) runs. Add a framework module only if
   the app uses it: dokimos-spring-ai, dokimos-langchain4j, or dokimos-koog.
3. Identify what to evaluate by reading the app. RAG or Q&A over retrieved context: use
   faithfulness, contextual relevance, hallucination, correctness. A tool-using agent:
   capture the run as an AgentTrace and use the agent evaluators (tool-call validity, tool
   correctness, trajectory, tool error, tool efficiency, task completion, argument
   hallucination, tool name and description reliability). Structured/JSON output: return a
   record from Task.typed, compare with StructuralMatchEvaluator, read back with
   actualOutputAs(...). Plain text: exact match, regex, or an LLM judge.
4. Read the matching page below (full text in llms-full.txt) before writing code: getting
   started and installation; the evaluators reference; agent and tool-call evaluation,
   which also covers the Spring AI, Spring AI Alibaba, LangChain4j, Koog, Embabel, and OpenAI
   trace extractors;
   structured and typed data (Task.typed, StructuralMatchEvaluator, actualOutputAs,
   resultJson/resultAs); datasets; experiments; the JUnit integration (@DatasetSource,
   Assertions.assertEval).
5. Write ONE eval first and make it run in the existing test suite. For CI, assert a
   threshold (for example assertThat(result.passRate()).isGreaterThan(0.9) or
   Assertions.assertEval(testCase, evaluator)) so the build fails when quality drops. Tell
   the user how to run it (mvn test or ./gradlew test).

## Rules

- LLM-judge evaluators need a JudgeLM; deterministic ones (validity, correctness,
  trajectory, tool error, tool efficiency, exact match, regex) do not. Prefer deterministic
  evaluators for CI gates.
- Agent evaluators read specific EvalTestCase keys (toolCalls, tools, tasks). Use
  AgentTrace.toTestCase(...) or a framework extractor rather than wiring keys by hand.
- Do not invent evaluator names or builder methods. If unsure, fetch the evaluators or
  agent-evaluation page and use the exact signature shown.
- For structured/JSON output, return a record from Task.typed (or typedTask in Kotlin) and
  compare with StructuralMatchEvaluator; read it back with actualOutputAs/expectedOutputAs/
  inputAs (use OutputType<T> for generics). For typed tool calls, store results with
  resultJson(...) and read them back with resultAs(...); read arguments with argumentsAs(...).

Skill registry for agents: /.well-known/skills/index.json`;

// Newest published release (v-prefixed tag) from GitHub, used for the changelog
// "Latest" badge and the install snippet version. Falls back to the hardcoded
// value if the API is unreachable (offline build, rate limit).
async function latestReleaseVersion(fallback: string): Promise<string> {
  try {
    const headers: Record<string, string> = {
      Accept: "application/vnd.github+json",
      "User-Agent": "dokimos-docs-build",
    };
    if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    const res = await fetch("https://api.github.com/repos/dokimos-dev/dokimos/releases/latest", {
      headers,
    });
    if (!res.ok) return fallback;
    const data = (await res.json()) as { tag_name?: string };
    const version = (data.tag_name ?? "").replace(/^v/, "");
    return /^\d+\.\d+\.\d+/.test(version) ? version : fallback;
  } catch {
    return fallback;
  }
}

const config: Config = {
  title: "Dokimos | LLM Evaluation Framework for Java",
  tagline: "An Evaluation Framework for LLM applications in Java.",
  favicon: "img/favicon.ico",
  staticDirectories: ["public", "static"],

  // Surfaced in the install snippets and the changelog "Latest" badge. Overridden
  // at build time from the newest GitHub release (see createConfig below); these
  // are the offline fallback.
  customFields: {
    dokimosVersion: "0.20.0",
    latestVersion: "0.20.0",
  },

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // Set the production url of your site here
  url: "https://dokimos.dev",
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: "/",

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: "dokimos-dev", // Usually your GitHub org/user name.
  projectName: "dokimos", // Usually your repo name.

  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",
  onDuplicateRoutes: "warn",

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: "en",
    locales: ["en"],
  },

  presets: [
    [
      "classic",
      {
        docs: {
          path: "docs",
          routeBasePath: "",
          sidebarPath: "./sidebars.ts",
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl: "https://github.com/dokimos-dev/dokimos/blob/master/docs/",
        },
        theme: {
          customCss: "./src/css/custom.css",
        },
      } satisfies Preset.Options,
    ],
  ],

  plugins: [
    [
      // Generates /llms.txt, /llms-full.txt, and a .md per page.
      "docusaurus-plugin-llms",
      {
        title: "Dokimos",
        description:
          "The LLM evaluation framework for Java and Kotlin. Evaluate responses and agent tool calls, run evals in JUnit and CI, and integrate with Spring AI, Spring AI Alibaba, LangChain4j, Koog, and Embabel.",
        generateMarkdownFiles: true,
        rootContent: LLMS_ROOT_CONTENT,
        fullRootContent: LLMS_ROOT_CONTENT,
        includeOrder: [
          "overview.md",
          "getting-started/*.md",
          "evaluation/agent-evaluation.md",
          "evaluation/*.md",
          "integrations/*.md",
          "server/*.md",
          "tutorials/*.md",
        ],
        ignoreFiles: ["changelog*"],
      },
    ],
    // Serves /.well-known/skills/index.json and each plugin's SKILL.md.
    "./plugins/skills-registry.js",
  ],

  themes: [
    [
      // Offline search index. src/theme/SearchBar owns the Cmd+K shortcut.
      require.resolve("@easyops-cn/docusaurus-search-local"),
      {
        hashed: true,
        indexBlog: false,
        docsRouteBasePath: "/",
        searchBarShortcut: false,
      },
    ],
  ],

  themeConfig: {
    colorMode: {
      defaultMode: "dark",
      respectPrefersColorScheme: false,
    },
    docs: {
      sidebar: {
        hideable: true,
      },
    },
    navbar: {
      title: "Dokimos",
      logo: {
        alt: "Dokimos Logo",
        src: "img/logo.svg",
      },
      items: [
        {
          type: "docSidebar",
          sidebarId: "tutorialSidebar",
          position: "left",
          label: "Overview",
          href: "/overview",
        },
        {
          label: "Javadoc",
          position: "left",
          href: "https://dokimos.dev/apidocs/",
        },
        {
          href: "https://github.com/dokimos-dev/dokimos",
          label: "GitHub",
          position: "right",
        },
      ],
    },
    prism: {
      theme: oneDarkInk,
      darkTheme: oneDarkInk,
      additionalLanguages: ["java"],
    },
  } satisfies Preset.ThemeConfig,
};

export default async function createConfig(): Promise<Config> {
  const latestVersion = await latestReleaseVersion("0.20.0");
  return {
    ...config,
    customFields: { ...config.customFields, dokimosVersion: latestVersion, latestVersion },
  };
}
