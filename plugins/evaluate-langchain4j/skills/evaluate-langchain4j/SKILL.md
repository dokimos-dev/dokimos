---
name: evaluate-langchain4j
description: Sets up evaluation of LangChain4j applications and RAG pipelines using Dokimos. Use this skill when the user wants to evaluate, test, or benchmark a LangChain4j app, RAG pipeline, or AI service. Also use when the user mentions LangChain4j evaluation, RAG testing, retrieval evaluation, or faithfulness/relevance checks with LangChain4j.
---

# Evaluate LangChain4j

Set up Dokimos evaluation for a LangChain4j application. The user will describe their application and evaluation goals via `$ARGUMENTS`.

## Where things live

- **LangChain4j support**: `dokimos-langchain4j/src/main/java/dev/dokimos/langchain4j/LangChain4jSupport.java`
- **Example**: `dokimos-examples/src/main/java/dev/dokimos/examples/langchain4j/LangChain4jRAGExample.java`
- **Maven dependency**: `dev.dokimos:dokimos-langchain4j`

Before writing code, read `LangChain4jSupport.java` to understand the available utilities.

## Key utilities

`LangChain4jSupport` provides:

- **`asJudge(ChatModel)`** — wraps a LangChain4j `ChatModel` into a `JudgeLM`
- **`simpleTask(ChatModel)`** — creates a `Task` for simple Q&A evaluation
- **`ragTask(Function<String, Result<String>>)`** — creates a `Task` for RAG evaluation that captures both output and retrieval context
- **`ragTask(..., inputKey, outputKey, contextKey)`** — RAG task with custom key names
- **`customTask(Task)`** — pass-through for full control
- **`extractTexts(List<Content>)`** — extracts text from LangChain4j Content objects

## Evaluation patterns

### Simple Q&A evaluation

```java
ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();

Task task = LangChain4jSupport.simpleTask(model);

ExperimentResult result = Experiment.builder()
        .name("QA Evaluation")
        .dataset(Dataset.fromJson(Path.of("datasets/qa.json")))
        .task(task)
        .evaluator(ExactMatchEvaluator.builder().build())
        .build()
        .run();
```

### RAG evaluation

The RAG task captures both the model output and the retrieved context, enabling evaluators like `FaithfulnessEvaluator` and `ContextualRelevanceEvaluator`:

```java
// 1. Build your LangChain4j AiService that returns Result<String>
interface Assistant {
    Result<String> chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .retrievalAugmentor(retrievalAugmentor)
        .build();

// 2. Create the RAG task
Task task = LangChain4jSupport.ragTask(assistant::chat);

// 3. Create a judge for LLM-based evaluators
JudgeLM judge = LangChain4jSupport.asJudge(judgeChatModel);

// 4. Run with RAG-specific evaluators
// ragTask() stores context under "context" key by default.
// FaithfulnessEvaluator and HallucinationEvaluator default to contextKey="context" (matches).
// ContextualRelevanceEvaluator defaults to retrievalContextKey="retrievalContext",
// so set it explicitly to match the ragTask output.
ExperimentResult result = Experiment.builder()
        .name("RAG Evaluation")
        .dataset(dataset)
        .task(task)
        .evaluators(List.of(
                FaithfulnessEvaluator.builder()
                        .judge(judge)
                        .threshold(0.7)
                        .build(),
                ContextualRelevanceEvaluator.builder()
                        .judge(judge)
                        .retrievalContextKey("context")
                        .threshold(0.5)
                        .build(),
                HallucinationEvaluator.builder()
                        .judge(judge)
                        .threshold(0.5)
                        .build()
        ))
        .build()
        .run();
```

## Dependencies

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-langchain4j</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

LangChain4j itself is a provided-scope dependency — the user must bring their own version.

## Evaluating an agent, not just RAG

If the LangChain4j app uses tools (an `AiService` whose method returns `Result<T>`), evaluate its tool calls with the agent evaluators. `LangChain4jSupport.toAgentTrace(result)` turns the run into an `AgentTrace`, and `toToolDefinitions(specs)` converts the tool specifications so the validity and reliability evaluators can see the tools the agent was given.

```java
Result<String> result = assistant.chat(userMessage);

AgentTrace trace = LangChain4jSupport.toAgentTrace(result);
List<ToolDefinition> tools = LangChain4jSupport.toToolDefinitions(toolSpecifications);

EvalTestCase testCase = trace.toTestCase(userMessage, tools);
var validity = ToolCallValidityEvaluator.builder().build().evaluate(testCase);
```

For the full agent evaluator set, use the `evaluate-agent` skill.

## Steps

1. Understand from `$ARGUMENTS` what the LangChain4j application does (Q&A, RAG, chat, etc.)
2. Determine if it's simple Q&A or RAG evaluation (RAG needs `Result<String>` return type)
3. Choose appropriate evaluators:
   - **Q&A**: `ExactMatchEvaluator`, `RegexEvaluator`, `LLMJudgeEvaluator`
   - **RAG**: `FaithfulnessEvaluator`, `ContextualRelevanceEvaluator`, `HallucinationEvaluator`, `PrecisionEvaluator`, `RecallEvaluator`
4. Create a dataset matching the application's domain
5. Wire everything together using `LangChain4jSupport` utilities
