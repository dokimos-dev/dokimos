# Knowledge Assistant Tutorial

This is the example code for the [Spring AI Agent Evaluation Tutorial](https://dokimos.dev/docs/tutorials/spring-ai-agent-evaluation).

## Quick Start

```bash
export OPENAI_API_KEY='your-api-key'
mvn spring-boot:run -pl dokimos-examples
```

Test the API:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is your return policy?"}'
```

## Run Evaluations

```bash
RUN_EVAL_TESTS=true OPENAI_API_KEY='your-api-key' mvn test -pl dokimos-examples -Dtest=KnowledgeAssistantEvaluationTest
```

Note: The test is skipped by default. Set `RUN_EVAL_TESTS=true` to run it.

## Structure

```
tutorial/
  KnowledgeAssistant.java         # RAG service
  KnowledgeAssistantController.java   # REST API
  KnowledgeAssistantApplication.java  # Spring Boot app
  VectorStoreConfig.java          # Document store
  evaluation/
    QAEvaluators.java             # Evaluator factory
    ResponseLengthEvaluator.java  # Custom evaluator

test/
  KnowledgeAssistantEvaluationTest.java
  resources/datasets/qa-dataset.json
```

See the [full tutorial](https://dokimos.dev/docs/tutorials/spring-ai-agent-evaluation) for detailed explanations.
