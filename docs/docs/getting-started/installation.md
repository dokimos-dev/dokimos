---
sidebar_position: 1
---

# Setup Dokimos in Java

## Getting started

Dokimos offers integrations with JUnit 5, LangChain4j, Spring AI, etc. Each of those provides a separate maven dependency.

If you haven't done so already, install `dokimos-core` in your project:

For Maven with a `pom.xml` file:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-core</groupId>
    <version>${dokimos.version}</version>
</dependency>
```

If you use Gradle with a `build.gradle` file:

```
implementation 'dev.dokimos:dokimos-core:${dokimosVersion}'
```
