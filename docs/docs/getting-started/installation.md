---
sidebar_position: 1
---

# Setup Dokimos in Java / Kotlin

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to add Dokimos to a Java or Kotlin project so you can start writing evaluations.

You only need one dependency to start: `dokimos-core`. Kotlin users add a second one for the DSL. Integrations (JUnit, LangChain4j, Spring AI, Spring AI Alibaba, Koog, Embabel) are extra dependencies you add when you want them.

## Step 1: Add the core dependency

Pick your build tool. If you write Kotlin, use the Kotlin tab to also get the `dokimos-kotlin` DSL.

<Tabs groupId="build-tool">
<TabItem value="maven" label="Maven" default>

Add this to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-core</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Kotlin projects also add the DSL:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-kotlin</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

Add this to your `build.gradle`:

```groovy
implementation 'dev.dokimos:dokimos-core:${dokimosVersion}'
```

Kotlin projects also add the DSL:

```groovy
implementation 'dev.dokimos:dokimos-kotlin:${dokimosVersion}'
```

</TabItem>
</Tabs>

Replace `${dokimos.version}` (or `${dokimosVersion}`) with the latest release. Dokimos is published to Maven Central under the `dev.dokimos` group.

## Step 2: Add an integration (optional)

Dokimos ships separate dependencies for the tools you already use. Add one only when you need it.

| Integration        | Artifact                    | Docs                                                               |
| ------------------ | --------------------------- | ------------------------------------------------------------------ |
| JUnit 5 / 6        | `dokimos-junit`             | [JUnit Integration](../integrations/junit)                         |
| LangChain4j        | `dokimos-langchain4j`       | [LangChain4j Integration](../integrations/langchain4j)             |
| Spring AI          | `dokimos-spring-ai`         | [Spring AI Integration](../integrations/spring-ai)                 |
| Spring AI Alibaba  | `dokimos-spring-ai-alibaba` | [Spring AI Alibaba Integration](../integrations/spring-ai-alibaba) |
| Koog               | `dokimos-koog`              | [Koog Integration](../integrations/koog)                           |
| Embabel (Java 21+) | `dokimos-embabel`           | [Embabel Integration](../integrations/embabel)                     |

Each integration page lists its exact dependency block. For example, to run evaluations as JUnit tests:

<Tabs groupId="build-tool">
<TabItem value="maven" label="Maven" default>

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```groovy
testImplementation 'dev.dokimos:dokimos-junit:${dokimosVersion}'
```

</TabItem>
</Tabs>

## Next steps

You are set up. Now write your first evaluation:

- [Your first evaluation](./evaluation) covers the core concepts: datasets, evaluators, tasks, and experiments.
- [JUnit Integration](../integrations/junit) walks through evaluating LLM output in a test.
- Read the integration page that matches your stack: [LangChain4j](../integrations/langchain4j), [Spring AI](../integrations/spring-ai), [Spring AI Alibaba](../integrations/spring-ai-alibaba), [Koog](../integrations/koog), or [Embabel](../integrations/embabel).
