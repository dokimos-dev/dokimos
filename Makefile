.PHONY: help clean compile test test-all build install package verify deps tree fmt check

# Default target
help:
	@echo "Dokimos Build Commands"
	@echo ""
	@echo "Build targets:"
	@echo "  make build        - Clean and install (skip tests)"
	@echo "  make compile      - Compile all modules"
	@echo "  make package      - Package JARs (skip tests)"
	@echo "  make install      - Install to local Maven repo (skip tests)"
	@echo "  make clean        - Clean all build artifacts"
	@echo ""
	@echo "Test targets:"
	@echo "  make test         - Run unit tests only"
	@echo "  make test-all     - Run unit + integration tests"
	@echo "  make verify       - Run full verification (compile, test, IT)"
	@echo ""
	@echo "Utility targets:"
	@echo "  make deps         - Download dependencies"
	@echo "  make tree         - Show dependency tree"
	@echo "  make fmt          - Format code (if formatter configured)"
	@echo "  make check        - Validate POM and check for updates"
	@echo "  make javadoc      - Generate Javadoc"
	@echo ""
	@echo "Module-specific (use MODULE=dokimos-core, etc.):"
	@echo "  make test-module  - Run tests for a specific module"
	@echo "  make build-module - Build a specific module"
	@echo ""
	@echo "Release targets:"
	@echo "  make release      - Clean install with signing"

# Build targets
clean:
	mvn clean

compile:
	mvn compile

package:
	mvn package -DskipTests

install:
	mvn install -DskipTests

build: clean install

# Test targets
test:
	mvn test

test-all:
	mvn verify

verify:
	mvn clean verify

# Utility targets
deps:
	mvn dependency:resolve

tree:
	mvn dependency:tree

fmt:
	mvn fmt:format 2>/dev/null || echo "No formatter plugin configured"

check:
	mvn validate
	@echo ""
	mvn versions:display-dependency-updates -q 2>/dev/null || true
	mvn versions:display-plugin-updates -q 2>/dev/null || true

javadoc:
	mvn javadoc:aggregate

# Module-specific targets
MODULE ?= dokimos-core

test-module:
	mvn test -pl $(MODULE) -am

build-module:
	mvn install -DskipTests -pl $(MODULE) -am

# Release targets
release:
	mvn clean install -Dsign
