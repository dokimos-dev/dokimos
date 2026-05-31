import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: "Dokimos | LLM Evaluation Framework for Java",
  tagline: "An Evaluation Framework for LLM applications in Java.",
  favicon: "img/favicon.ico",
  staticDirectories: ["public", "static"],

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
      theme: prismThemes.oneDark,
      darkTheme: prismThemes.oneDark,
      additionalLanguages: ["java"],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
