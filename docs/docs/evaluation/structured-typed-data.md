---
sidebar_position: 6
---

# Structured & Typed Data

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Dokimos supports typed, structured data end to end.

The input, output, expected, and metadata maps hold `Object` values, so it's easy to assume Dokimos is a string-in, string-out framework. It isn't. A task can produce a real domain object — a record, a POJO, a list — Dokimos can compare it structurally, and you can read it back type-safely wherever you need it. The same holds for tool-call results in agent evaluation. This page narrates that whole pipeline in one place:

1. **Author** a typed output from your task (`Task.typed` / `typedTask`).
2. **Compare** structured values (`StructuralMatchEvaluator`).
3. **Read back** typed values in a custom evaluator (`actualOutputAs` / `expectedOutputAs` / `inputAs`, with `OutputType<T>` for generics, and Kotlin reified `*As<T>()`).
4. **Judge** a structured value with an LLM judge that renders it as JSON.
5. **Type your tool calls** in agent evaluation (`resultJson` / `resultAs`, `argumentsAs`).
6. **Read typed metadata** (`metadataAs`).

Each step is self-contained, but they're designed to fit together: a task returns a record, the same record is compared and read back as a real object, and a sequential agent's `output -> input -> output` chain stays assertable because each tool result is typed.

## 1. Author a typed output

`Task.typed(fn)` wraps a function that returns a single value and stores it under the conventional `"output"` key. There is no `Map.of("output", ...)` boilerplate, and the value you store is the value you built. In Kotlin, the reified `typedTask<T> { ... }` DSL does the same thing.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
record Whisky(String name, String region, int age) {}

Task task = Task.typed(example -> {
    String json = llm.chat(example.input());
    return Json.parseWhisky(json); // returns a Whisky record
});
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
data class Whisky(val name: String, val region: String, val age: Int)

val task = typedTask<Whisky> { example ->
    val json = llm.chat(example.input())
    parseWhisky(json) // returns a Whisky
}
```

Inside `experiment { ... }` you can set it directly with the `typedTask` builder method:

```kotlin
val experiment = experiment {
    name = "Whisky extraction"
    dataset(whiskyDataset)
    typedTask<Whisky> { example -> parseWhisky(llm.chat(example.input())) }
    evaluator(StructuralMatchEvaluator())
}
```

  </TabItem>
</Tabs>

:::note
`Task.typed` rejects a `null` return with `NullPointerException` — the output map cannot hold a null value. If your function already returns a `Map`, that map is used directly as the output map rather than being nested under `"output"`, so a multi-key task can adopt `typed` without double-nesting.
:::

See the full reference for the typed-output accessors and the conversion contract in [Data Model → Typed outputs](./datamodel.md#typed-outputs).

## 2. Compare structured values

`StructuralMatchEvaluator` compares the actual output against the expected output as **JSON structures** rather than as opaque strings. A record, a `Map`, or a JSON string all compare object-against-object, so reformatting, key ordering, and numeric representation (`5` vs `5.0`) never count as a difference. This is the natural partner for a typed task: store a record under `"output"`, compare it here.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator structural = StructuralMatchEvaluator.builder()
    .name("Whisky Match")
    .threshold(1.0)
    .build();  // STRICT mode, outputKey "output", partial scoring
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val structural: Evaluator = StructuralMatchEvaluator.builder()
    .name("Whisky Match")
    .threshold(1.0)
    .build()  // STRICT mode, outputKey "output", partial scoring
```

  </TabItem>
</Tabs>

For comparison modes (`STRICT` vs `LENIENT`), partial-vs-`binary()` scoring, and the `outputKey(...)` option, see [Evaluators → StructuralMatchEvaluator](./evaluators.md#structuralmatchevaluator).

## 3. Read typed values back

A custom evaluator (or any code holding an `EvalTestCase`) can read the structured value back as a real object instead of parsing a string. Both `EvalTestCase` and `Example` expose typed accessors. For a non-generic target, pass a `Class<T>`; for a generic target like `List<Whisky>`, pass an `OutputType<T>` super-type token instantiated as an **anonymous subclass** so the element type is recorded.

| Method | Reads | Default key |
|--------|-------|-------------|
| `actualOutputAs(Class<T>)` / `actualOutputAs(OutputType<T>)` | actual output | `"output"` |
| `expectedOutputAs(Class<T>)` / `expectedOutputAs(OutputType<T>)` | expected output | `"output"` |
| `inputAs(Class<T>)` / `inputAs(OutputType<T>)` | input | `"input"` |
| `metadataAs(String, Class<T>)` / `metadataAs(String, OutputType<T>)` | metadata under `key` | (key required) |

Each accessor has a keyed overload (`actualOutputAs(String, Class<T>)`, `inputAs(String, OutputType<T>)`, and so on) for reading any other key. `Example` carries the `expectedOutputAs(...)` and `inputAs(...)` twins (it has no actual output yet); `EvalTestCase` carries all of them.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
public class WhiskyEvaluator implements Evaluator {
    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
        // Non-generic targets: pass a Class<T>
        Whisky actual = testCase.actualOutputAs(Whisky.class);
        Whisky expected = testCase.expectedOutputAs(Whisky.class);

        // The input was itself a typed request object
        WhiskyQuery query = testCase.inputAs(WhiskyQuery.class);

        // Generic targets: pass an OutputType<T> anonymous subclass
        List<Whisky> shortlist =
            testCase.actualOutputAs("shortlist", new OutputType<List<Whisky>>() {});

        boolean match = actual != null
            && actual.region().equals(expected.region());

        return EvalResult.builder()
            .name("Whisky Region")
            .score(match ? 1.0 : 0.0)
            .success(match)
            .reason(match ? "Region matches" : "Wrong region")
            .build();
    }

    @Override
    public String name() { return "Whisky Region"; }

    @Override
    public double threshold() { return 1.0; }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
class WhiskyEvaluator : Evaluator {
    override fun evaluate(testCase: EvalTestCase): EvalResult {
        // Java-style: pass a Class<T> or an OutputType<T> anonymous subclass
        val actual = testCase.actualOutputAs(Whisky::class.java)
        val expected = testCase.expectedOutputAs(Whisky::class.java)

        // Kotlin reified accessors infer the type — no Class or token needed
        val query = testCase.inputAs<WhiskyQuery>()
        val shortlist = testCase.actualOutputAs<List<Whisky>>("shortlist")

        val match = actual != null && actual.region == expected?.region

        return EvalResult(
            name = "Whisky Region",
            score = if (match) 1.0 else 0.0,
            success = match,
            reason = if (match) "Region matches" else "Wrong region",
        )
    }

    override fun name(): String = "Whisky Region"

    override fun threshold(): Double = 1.0
}
```

The Kotlin reified `*As<T>()` extensions (`actualOutputAs<T>()`, `expectedOutputAs<T>()`, `inputAs<T>()`, `metadataAs<T>(key)`, and their keyed overloads) infer the target type from the call site, so you skip both `Class<T>` and `OutputType<T>` — including for generic types like `List<Whisky>`. They convert through a Kotlin-aware Jackson mapper, so a plain Kotlin data class reads back with no Jackson annotations (`@JsonCreator` / `@JsonProperty`) — its constructor parameter names, nullable fields, and defaults are all honored.

  </TabItem>
</Tabs>

:::tip
Constructing an `OutputType` raw (`new OutputType() {}`) throws `IllegalArgumentException` — there is no type argument to capture. Use the `Class<T>` accessors for non-generic targets and reach for `OutputType<T>` only when the target is generic. In Kotlin the reified `*As<T>()` form handles both.
:::

The conversion contract is shared across every accessor: an absent key returns `null`; a value already of the target type is returned as-is; anything else is converted via Jackson, and a value that cannot be converted throws `DokimosTypeConversionException` (in `dev.dokimos.core.exceptions`). The full contract is documented in [Data Model → Conversion contract](./datamodel.md#conversion-contract).

## 4. Judge a structured value as JSON

`LLMJudgeEvaluator` can judge a structured value directly. When the output is a record, `Map`, or list, the judge renders it as pretty-printed JSON before sending it to the model; String and primitive output is passed through verbatim. So you don't have to flatten a structured result into prose just to judge it — return the object and let the judge read the JSON.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Evaluator wellFormed = LLMJudgeEvaluator.builder()
    .name("Extraction Quality")
    .criteria("Is the extracted whisky record complete and plausible for the source text?")
    .evaluationParams(List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
    .judge(judge)
    .threshold(0.8)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val wellFormed: Evaluator = llmJudge(judge) {
    name = "Extraction Quality"
    criteria = "Is the extracted whisky record complete and plausible for the source text?"
    params(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT)
    threshold = 0.8
}
```

  </TabItem>
</Tabs>

## 5. Typed tool calls

In agent evaluation, a `ToolCall` carries a single string `result`. When a tool produced a structured value, `resultJson(Object)` serializes it to a compact JSON string and stores it in the same `result` component, so you stop hand-escaping JSON. Read it back type-safely with `resultAs(Class<T>)` or `resultAs(OutputType<T>)` — the symmetric counterpart. This is what makes a sequential agent's `output -> input -> output` chain assertable: capture each step's structured result, then read it back as a real object. Tool-call arguments read back the same way with `argumentsAs(Class<T>)` / `argumentsAs(OutputType<T>)`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
record Confirmation(String confirmation, double total) {}

// Write — serialize the value, no escaping
ToolCall call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 3)
    .resultJson(new Confirmation("ABC123", 540.0))
    .build();

// Read back — typed
Confirmation booked = call.resultAs(Confirmation.class);   // structured result
HotelArgs args = call.argumentsAs(HotelArgs.class);        // typed arguments
List<Confirmation> many =
    call.resultAs(new OutputType<List<Confirmation>>() {}); // generics via OutputType
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
data class Confirmation(val confirmation: String, val total: Double)

// Write — serialize the value, no escaping
val call = ToolCall.builder()
    .name("book_hotel")
    .argument("city", "Paris")
    .argument("nights", 3)
    .resultJson(Confirmation("ABC123", 540.0))
    .build()

// Read back — typed
val booked = call.resultAs(Confirmation::class.java)   // structured result
val args = call.argumentsAs(HotelArgs::class.java)     // typed arguments
val many = call.resultAs(object : OutputType<List<Confirmation>>() {}) // generics
```

  </TabItem>
</Tabs>

:::note
`resultJson` and `resultAs` operate on the same `result` field, so downstream evaluators (`ToolErrorEvaluator`, the hallucination judge, and anything reading `ToolCall.result()`) see an identical string either way. `resultAs` parses that string as JSON: a `null` or blank result returns `null`, and a raw non-JSON string from `result(String)` is not parseable — use `result()` for that.
:::

For the full agent data model and where these read back into evaluators, see [Agent Evaluation → ToolCall](./agent-evaluation.md#toolcall).

## 6. Typed metadata

Metadata is just as typed as the rest. `metadataAs(key, Class<T>)` and `metadataAs(key, OutputType<T>)` read a metadata value back as a real object — useful when you stash a structured rubric, a list of expected entities, or any configuration object alongside an example. Because metadata has no conventional key, the key is always required.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Rubric rubric = testCase.metadataAs("rubric", Rubric.class);
List<String> tags = testCase.metadataAs("tags", new OutputType<List<String>>() {});
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val rubric = testCase.metadataAs<Rubric>("rubric")     // reified
val tags = testCase.metadataAs<List<String>>("tags")   // reified, generic
```

  </TabItem>
</Tabs>

The same conversion contract applies: absent key returns `null`, an already-typed value is returned as-is, and an unconvertible value throws `DokimosTypeConversionException`.

## Where to go next

- [Data Model → Typed outputs](./datamodel.md#typed-outputs) — the full accessor reference and conversion contract.
- [Evaluators → StructuralMatchEvaluator](./evaluators.md#structuralmatchevaluator) — comparison modes and scoring.
- [Agent Evaluation → ToolCall](./agent-evaluation.md#toolcall) — typed tool-call results in the agent data model.
