// Copies each plugin's SKILL.md into /.well-known/skills/<name>/SKILL.md and
// writes an index.json, generated from .claude-plugin/marketplace.json.
const fs = require("fs");
const path = require("path");

module.exports = function skillsRegistryPlugin(context) {
  const repoRoot = path.resolve(context.siteDir, "..");
  return {
    name: "dokimos-skills-registry",
    async postBuild({ outDir, siteConfig }) {
      const marketplace = JSON.parse(
        fs.readFileSync(path.join(repoRoot, ".claude-plugin", "marketplace.json"), "utf8"),
      );
      const base = (siteConfig.url || "").replace(/\/$/, "");
      const outRoot = path.join(outDir, ".well-known", "skills");

      const skills = marketplace.plugins.map((p) => {
        const rel = path.join("plugins", p.name, "skills", p.name, "SKILL.md");
        const dest = path.join(outRoot, p.name, "SKILL.md");
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        fs.copyFileSync(path.join(repoRoot, rel), dest);
        return {
          name: p.name,
          description: p.description,
          category: p.category,
          tags: p.tags,
          url: `${base}/.well-known/skills/${p.name}/SKILL.md`,
        };
      });

      const index = {
        name: "Dokimos Skills",
        description:
          "Agent-readable skills for evaluating LLM and AI-agent code with Dokimos, the LLM evaluation framework for Java and Kotlin.",
        homepage: base || "https://dokimos.dev",
        instructions:
          "Dokimos changes each release. Fetch the matching skill SKILL.md and treat it as authoritative; do not rely on pre-training. See /llms.txt for usage.",
        skills,
      };
      fs.mkdirSync(outRoot, { recursive: true });
      fs.writeFileSync(path.join(outRoot, "index.json"), JSON.stringify(index, null, 2));
    },
  };
};
