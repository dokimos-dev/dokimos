---
sidebar_position: 1
---

# Migrating from dokimos-junit5 to dokimos-junit

Starting with version 0.8.0, the `dokimos-junit5` module has been renamed to `dokimos-junit` to support both JUnit 5.x and JUnit 6.x.

## Why the Change?

JUnit 6.0 was released on September 30, 2025 as the next generation of JUnit. While it maintains backward compatibility with the Jupiter APIs used by JUnit 5.x, it introduces:

- **Java 17 minimum baseline** (dokimos already requires Java 17)
- **Unified version numbering** across all JUnit modules
- **Removal of deprecated APIs**

The renamed `dokimos-junit` module works seamlessly with both JUnit 5.x and JUnit 6.x, so you don't need to change your test code when upgrading JUnit versions.

## Required Changes

### 1. Update Maven Dependency

```xml
<!-- Before -->
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit5</artifactId>
    <version>0.7.x</version>
    <scope>test</scope>
</dependency>

<!-- After -->
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-junit</artifactId>
    <version>0.8.0</version>
    <scope>test</scope>
</dependency>
```

### 2. Update Gradle Dependency

```groovy
// Before
testImplementation 'dev.dokimos:dokimos-junit5:0.7.x'

// After
testImplementation 'dev.dokimos:dokimos-junit:0.8.0'
```

### 3. Update Import Statements

The package name has changed from `dev.dokimos.junit5` to `dev.dokimos.junit`:

```java
// Before
import dev.dokimos.junit5.DatasetSource;

// After
import dev.dokimos.junit.DatasetSource;
```