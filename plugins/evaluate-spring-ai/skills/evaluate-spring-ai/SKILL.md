---
name: evaluate-spring-ai
description: Sets up evaluation of Spring AI applications using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark a Spring AI app, ChatClient, RAG pipeline, or advisor chain. Also use when the user mentions Spring AI evaluation, Spring Boot LLM testing, or integrating Dokimos with Spring AI projects.
---

# Evaluate Spring AI

Set up Dokimos evaluation for a Spring AI application. The user will describe their application and evaluation goals via `$ARGUMENTS`.

## Where things live

- **Spring AI support**: `dokimos-spring-ai/src/main/java/dev/dokimos/springai/SpringAiSupport.java`
- **Examples**: `dokimos-examples/src/main/java/dev/dokimos/examples/springai/`
- **Full Spring Boot example**: `dokimos-examples/src/main/java/dev/dokimos/examples/springai/tutorial/`
- **Maven dependency**: `dev.dokimos:dokimos-spring-ai`

Before writing code, read `SpringAiSupport.java` to understand the available utilities.

## Key utilities

`SpringAiSupport` provides:

- **`asJudge(ChatClient.Builder)`** — wraps a Spring AI `ChatClient.Builder` into a `JudgeLM`
- **`asJudge(ChatModel)`** — wraps a `ChatModel` directly into a `JudgeLM`
- **`toTestCase(EvaluationRequest)`** — converts Spring AI's `EvaluationRequest` to Dokimos `EvalTestCase`
- **`toEvaluationResponse(EvalResult)`** — converts Dokimos `EvalResult` back to Spring AI `EvaluationResponse`

## Evaluation patterns

### Simple ChatClient evaluation

```java
@SpringBootTest
class MyChatEvaluationTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void evaluateChatbot() {
        ChatClient chatClient = chatClientBuilder.build();

        Task task = example -> {
            String response = chatClient.prompt()
                    .user(example.input())
                    .call()
                    .content();
            return Map.of("output", response);
        };

        JudgeLM judge = SpringAiSupport.asJudge(chatClientBuilder);

        ExperimentResult result = Experiment.builder()
                .name("Chatbot Evaluation")
                .dataset(Dataset.fromJson(Path.of("src/test/resources/datasets/qa.json")))
                .task(task)
                .evaluator(LLMJudgeEvaluator.builder()
                        .name("answer-quality")
                        .judge(judge)
                        .criteria("Is the response helpful and accurate?")
                        .evaluationParams(List.of(
                                EvalTestCaseParam.INPUT,
                                EvalTestCaseParam.ACTUAL_OUTPUT,
                                EvalTestCaseParam.EXPECTED_OUTPUT))
                        .threshold(0.7)
                        .build())
                .build()
                .run();
    }
}
```

### RAG evaluation with advisors

```java
Task task = example -> {
    String input = example.input();
    ChatClient.ChatClientRequestSpec request = chatClient.prompt().user(input);
    request.advisors(new QuestionAnswerAdvisor(vectorStore));

    String response = request.call().content();
    List<Document> docs = vectorStore.similaritySearch(input);
    List<String> context = docs.stream().map(Document::getText).toList();

    return Map.of("output", response, "context", context);
};
```

### Converting between Spring AI and Dokimos types

```java
EvaluationRequest request = new EvaluationRequest(userText, documents, responseContent);
EvalTestCase testCase = SpringAiSupport.toTestCase(request);
EvalResult result = evaluator.evaluate(testCase);
EvaluationResponse response = SpringAiSupport.toEvaluationResponse(result);
```

## Dependencies

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-spring-ai</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

Spring AI itself is a provided-scope dependency — the user must bring their own version.

## Evaluating an agent, not just chat or RAG

If the Spring AI app calls tools, evaluate its tool calls with the agent evaluators. An `AssistantMessage` carries the tool calls the model made; the results come back in the `ToolResponseMessage`s. `SpringAiSupport.toAgentTrace(assistantMessage, toolResponseMessages)` builds an `AgentTrace` (results matched to calls by tool-call id), and `toToolDefinitions(defs)` converts the tool definitions.

```java
AgentTrace trace = SpringAiSupport.toAgentTrace(assistantMessage, toolResponseMessages);
List<ToolDefinition> tools = SpringAiSupport.toToolDefinitions(toolDefinitions);

EvalTestCase testCase = trace.toTestCase(userMessage, tools);
var validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
```

For the full agent evaluator set, use the `evaluate-agent` skill.

## Steps

1. Understand from `$ARGUMENTS` what the Spring AI application does
2. Determine if it's a simple ChatClient app or uses RAG advisors
3. Choose appropriate evaluators for the use case
4. Create a dataset matching the application's domain
5. Wire evaluation using `SpringAiSupport` utilities
6. For Spring Boot apps, set up tests with `@SpringBootTest`
