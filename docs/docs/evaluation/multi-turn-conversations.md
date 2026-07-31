---
sidebar_position: 5
---

# Multi-Turn Conversations

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to test a chat assistant across a full back-and-forth conversation, not just one prompt and reply.

Single-turn tests check one answer. Real users keep talking. They follow up, change their mind, and get frustrated. To test that, you need to drive a whole conversation and then judge how it went. Dokimos gives you four pieces to do that:

- **Simulated users**: an LLM that plays a role and types like a real person (an angry customer, a confused user, a technical expert).
- **Conversation simulator**: takes turns between your app and the simulated user until the chat ends.
- **Trajectory evaluator**: scores the whole conversation with an LLM as the judge.
- **Golden generator**: turns scenario seeds into a reusable conversation dataset for your test suite.

## Quick Example

Here is the full loop: build a fake user, wrap your app, run the chat, then grade it. Copy this and replace `chatClient` and `judgeLM` with your own.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// 1. Create a simulated user (frustrated customer)
SimulatedUser user = UserPersonas.aggressiveCustomer(judgeLM);

// 2. Wrap your application
ConversationalApplication app = trajectory -> {
    String response = chatClient.chat(formatHistory(trajectory));
    return Message.assistant(response);
};

// 3. Run the simulation
ConversationTrajectory trajectory = ConversationSimulator.builder()
    .simulatedUser(user)
    .application(app)
    .maxTurns(8)
    .scenario("Handle product return request")
    .initialMessage("I want to return this defective product!")
    .build()
    .simulate();

// 4. Evaluate the conversation
EvalResult result = TrajectoryEvaluator.builder()
    .name("Customer Service Quality")
    .threshold(0.7)
    .judge(judgeLM)
    .criteria(List.of(
        TrajectoryEvaluationCriteria.userSatisfaction(),
        TrajectoryEvaluationCriteria.problemResolution()
    ))
    .build()
    .evaluate(EvalTestCase.builder()
        .actualOutput("trajectory", trajectory)
        .build());
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// 1. Create a simulated user (frustrated customer)
val user: SimulatedUser = UserPersonas.aggressiveCustomer(judgeLM)

// 2. Wrap your application
val app: ConversationalApplication = ConversationalApplication { trajectory ->
    val response = chatClient.chat(formatHistory(trajectory))
    Message.assistant(response)
}

// 3. Run the simulation
val trajectory = simulator {
    simulatedUser = user
    application = app
    maxTurns = 8
    scenario = "Handle product return request"
    initialMessage = "I want to return this defective product!"
}.simulate()

// 4. Evaluate the conversation
val result = trajectoryEvaluator(judgeLM) {
    name = "Customer Service Quality"
    threshold = 0.7
    criteria(listOf(
            TrajectoryEvaluationCriteria.userSatisfaction(),
            TrajectoryEvaluationCriteria.problemResolution()
    ))
}
    .evaluate(
        EvalTestCase(
            actualOutputs = mapOf("trajectory" to trajectory)
        )
    )
```

  </TabItem>
</Tabs>

The rest of this page breaks down each step.

## Core Concepts

### Messages and Trajectories

A conversation is a list of messages. Each message has a role: user, assistant, or system. Build one with the matching factory method.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
Message userMsg = Message.user("I need help with my order");
Message assistantMsg = Message.assistant("I'd be happy to help. What's your order number?");
Message systemMsg = Message.system("You are a helpful support agent");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val userMsg = Message.user("I need help with my order")
val assistantMsg = Message.assistant("I'd be happy to help. What's your order number?")
val systemMsg = Message.system("You are a helpful support agent")
```

  </TabItem>
</Tabs>

A `ConversationTrajectory` holds the whole conversation. The simulator builds one for you, but you can also build one by hand to test a fixed transcript.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ConversationTrajectory trajectory = ConversationTrajectory.builder()
    .scenario("Customer support interaction")
    .userMessage("I need help")
    .assistantMessage("How can I assist you?")
    .userMessage("My order is late")
    .assistantMessage("Let me check that for you")
    .build();

// Methods you will use
trajectory.turnCount();           // Number of complete turns
trajectory.userMessages();        // All user messages
trajectory.assistantMessages();   // All assistant messages
trajectory.lastMessage();         // Most recent message
trajectory.toJson();              // JSON for debugging
trajectory.toText();              // Plain text transcript
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val trajectory = trajectory {
    scenario = "Customer support interaction"
    user("I need help")
    assistant("How can I assist you?")
    user("My order is late")
    assistant("Let me check that for you")
}

// Methods you will use
trajectory.turnCount()           // Number of complete turns
trajectory.userMessages()        // All user messages
trajectory.assistantMessages()   // All assistant messages
trajectory.lastMessage()         // Most recent message
trajectory.toJson()              // JSON for debugging
trajectory.toText()              // Plain text transcript
```

  </TabItem>
</Tabs>

### Tool Calls on Turns

A real agent calls tools mid-conversation: it looks up the weather, searches flights, then books a hotel. An assistant turn can carry the tool calls it made, so you can score *what the agent did each turn*, not just what it said.

Attach a typed `List<ToolCall>` to an assistant turn. A turn that called no tools needs no change.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ConversationTrajectory trajectory = ConversationTrajectory.builder()
    .userMessage("What's the weather in Paris?")
    .assistantMessage("It's 18C and sunny.", List.of(
        ToolCall.builder().name("get_weather").argument("city", "Paris").result("18C, sunny").build()
    ))
    .userMessage("Book me a hotel there.")
    .assistantMessage("Booked the Hotel Le Marais.", List.of(
        ToolCall.of("book_hotel", Map.of("city", "Paris"))
    ))
    .userMessage("Thanks!")
    .assistantMessage("You're all set!") // tool-free turn, unchanged
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val trajectory = trajectory {
    user("What's the weather in Paris?")
    assistant("It's 18C and sunny.", listOf(
        ToolCall.builder().name("get_weather").argument("city", "Paris").result("18C, sunny").build()
    ))
    user("Book me a hotel there.")
    assistant("Booked the Hotel Le Marais.", listOf(
        ToolCall.of("book_hotel", mapOf("city" to "Paris"))
    ))
    user("Thanks!")
    assistant("You're all set!") // tool-free turn, unchanged
}
```

  </TabItem>
</Tabs>

`Message` carries the tool calls as a typed `List<ToolCall>`; an assistant message built without them returns an empty list. When your app produces a turn, attach the calls with `Message.assistant(content, toolCalls)`.

#### Per-Turn Evaluation (Primary Path)

This is the recommended way to grade tool use across a conversation. `toolCallsByTurn()` returns one tool-call list per assistant turn, in order. Pair each turn with the calls you expected and run the [deterministic agent evaluators](./agent-evaluation.md), with no LLM and no API key.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
List<List<ToolCall>> actualByTurn = trajectory.toolCallsByTurn();
List<List<ToolCall>> expectedByTurn = List.of(
    List.of(ToolCall.of("get_weather", Map.of())),
    List.of(ToolCall.of("book_hotel", Map.of())),
    List.of() // final turn calls no tools
);

var validity = ToolCallValidityEvaluator.builder().build();
var correctness = ToolCorrectnessEvaluator.builder().build();

for (int turn = 0; turn < actualByTurn.size(); turn++) {
    EvalTestCase turnCase = EvalTestCase.builder()
        .actualOutput("toolCalls", actualByTurn.get(turn))
        .expectedOutput("toolCalls", expectedByTurn.get(turn))
        .metadata("tools", tools)
        .build();

    EvalResult v = validity.evaluate(turnCase);
    EvalResult c = correctness.evaluate(turnCase);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val actualByTurn = trajectory.toolCallsByTurn()
val expectedByTurn = listOf(
    listOf(ToolCall.of("get_weather", mapOf())),
    listOf(ToolCall.of("book_hotel", mapOf())),
    listOf<ToolCall>() // final turn calls no tools
)

val validity = ToolCallValidityEvaluator.builder().build()
val correctness = ToolCorrectnessEvaluator.builder().build()

actualByTurn.forEachIndexed { turn, calls ->
    val turnCase = EvalTestCase.builder()
        .actualOutput("toolCalls", calls)
        .expectedOutput("toolCalls", expectedByTurn[turn])
        .metadata("tools", tools)
        .build()

    val v = validity.evaluate(turnCase)
    val c = correctness.evaluate(turnCase)
}
```

  </TabItem>
</Tabs>

:::note
`toolCallsByTurn()` groups by **assistant message**, which can differ from `turnCount()` (user/assistant pairs) when a conversation has consecutive or leading assistant messages. Each inner list lines up with `assistantMessages()`.
:::

See [`MultiTurnToolCallExample.java`](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/conversation/MultiTurnToolCallExample.java) for a complete runnable version.

#### Whole-Conversation Shortcuts

When you want to assert over the whole conversation rather than per turn, build a test case straight from the trajectory.

- `toolCalls()`: every turn's calls flattened into one list, in order.
- `toTestCase()` and `toTestCase(tools)`: a **deterministic** test case. The flattened `toolCalls` go in the actual outputs, the input is the **last user message**, and `tools` (when given) go in metadata. As-is, it feeds the rule-based evaluators that read only actual outputs (validity, error, efficiency). `ToolCorrectnessEvaluator` and `ToolTrajectoryEvaluator` additionally need an expected list, which this path does not set; wire one in yourself (for example, `EvalTestCase.builder().expectedOutput("toolCalls", expected)`) or they throw an `EvaluationException`.
- `toTestCase(tools, tasks)`: the **judge** test case for `TaskCompletionEvaluator` and `ToolArgumentHallucinationEvaluator`. Its input is the rendered transcript of the whole conversation, but tool calls are rendered **name-only** (`[tool: name]`, not `[tool: name(args)]`) so the argument values a hallucination judge assesses never appear in the grounding it reads; the arguments stay available through the actual outputs. No separate output is set, so the transcript is not double-wrapped.
- `toAgentTrace()` / `toAgentOutputs()`: collapse the conversation into a single `AgentTrace` (or its output map) for the standard agent data flow.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Deterministic: input is the last user message, calls are flattened across turns
EvalTestCase deterministic = trajectory.toTestCase(tools);
EvalResult validity = ToolCallValidityEvaluator.builder().build().evaluate(deterministic);

// Judge: input is the transcript (tool calls name-only), tasks listed in metadata
EvalTestCase judgeCase = trajectory.toTestCase(tools, List.of("Check weather", "Book a hotel"));
EvalResult completion = TaskCompletionEvaluator.builder().judge(judgeLM).build().evaluate(judgeCase);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Deterministic: input is the last user message, calls are flattened across turns
val deterministic = trajectory.toTestCase(tools)
val validity = ToolCallValidityEvaluator.builder().build().evaluate(deterministic)

// Judge: input is the transcript (tool calls name-only), tasks listed in metadata
val judgeCase = trajectory.toTestCase(tools, listOf("Check weather", "Book a hotel"))
val completion = TaskCompletionEvaluator.builder().judge(judgeLM).build().evaluate(judgeCase)
```

  </TabItem>
</Tabs>

#### Tool Calls in the Transcript

`toText()` and `toJson()` render each turn's tool calls. `toText()` adds one compact `[tool: name(args)]` line per call under the message; `toJson()` adds a `toolCalls` array to a turn that has any. A tool-free conversation renders exactly as before, byte-identical, so adding tool calls to one turn never reshapes the rest.

To let the trajectory judge reason over tool usage, turn it on with `includeToolCalls(true)`. It is off by default, so existing judge suites see an unchanged prompt.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
    .name("Support Quality")
    .judge(judgeLM)
    .criteria(List.of(TrajectoryEvaluationCriteria.goalCompletion()))
    .includeToolCalls(true) // render each turn's tool calls in the judge prompt
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// includeToolCalls is on the Java builder; call it directly from Kotlin
val evaluator = TrajectoryEvaluator.builder()
    .name("Support Quality")
    .judge(judgeLM)
    .criteria(listOf(TrajectoryEvaluationCriteria.goalCompletion()))
    .includeToolCalls(true) // render each turn's tool calls in the judge prompt
    .build()
```

  </TabItem>
</Tabs>

### Simulated Users

A simulated user types the user side of the chat. The `SimulatedUser` interface takes the conversation so far and returns the next user message.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@FunctionalInterface
public interface SimulatedUser {
    Message generateMessage(ConversationTrajectory trajectory);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
fun interface SimulatedUser {
    fun generateMessage(trajectory: ConversationTrajectory): Message
}
```

  </TabItem>
</Tabs>

#### LLM-Based Simulated User

`LLMSimulatedUser` uses an LLM to write each message. Give it a persona and a few behavior rules, and it stays in character across turns.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
SimulatedUser user = LLMSimulatedUser.builder()
    .judge(judgeLM)
    .persona("impatient customer who is in a hurry")
    .behaviorGuidelines("""
        - Express time pressure
        - Ask for quick solutions
        - Show frustration with long explanations
        """)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val user: SimulatedUser = llmUser(judgeLM) {
    persona = "impatient customer who is in a hurry"
    behaviorGuidelines = """
        - Express time pressure
        - Ask for quick solutions
        - Show frustration with long explanations
    """
}
```

  </TabItem>
</Tabs>

Want the conversation to start the same way every run? Set fixed responses for the opening turns.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
SimulatedUser user = LLMSimulatedUser.builder()
    .judge(judgeLM)
    .persona("customer with a complaint")
    .fixedResponses(List.of(
        "I ordered a blue shirt but received a red one!",
        "I want a full refund, not a replacement"
    ))
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val user: SimulatedUser = llmUser(judgeLM) {
    persona = "customer with a complaint"
    fixedResponses(listOf(
            "I ordered a blue shirt but received a red one!",
            "I want a full refund, not a replacement"
    ))
}
```

  </TabItem>
</Tabs>

The simulated user sends each fixed response in order, one per turn. After the list runs out, the LLM takes over and writes contextual replies.

#### Pre-Built Personas

`UserPersonas` ships ready-made characters for common tests. Pass your `judgeLM` and you get a configured `SimulatedUser`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Customer service
UserPersonas.aggressiveCustomer(judgeLM)  // Frustrated, demanding
UserPersonas.confusedUser(judgeLM)        // Needs clarification
UserPersonas.impatientUser(judgeLM)       // Wants quick answers
UserPersonas.satisfiedCustomer(judgeLM)   // Cooperative, positive

// Technical users
UserPersonas.technicalExpert(judgeLM)     // Uses jargon, probes details
UserPersonas.noviceUser(judgeLM)          // Needs basic explanations

// Edge cases
UserPersonas.adversarialUser(judgeLM)     // Tests boundaries (red-teaming)
UserPersonas.offTopicUser(judgeLM)        // Goes on tangents
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Customer service
UserPersonas.aggressiveCustomer(judgeLM)  // Frustrated, demanding
UserPersonas.confusedUser(judgeLM)        // Needs clarification
UserPersonas.impatientUser(judgeLM)       // Wants quick answers
UserPersonas.satisfiedCustomer(judgeLM)   // Cooperative, positive

// Technical users
UserPersonas.technicalExpert(judgeLM)     // Uses jargon, probes details
UserPersonas.noviceUser(judgeLM)          // Needs basic explanations

// Edge cases
UserPersonas.adversarialUser(judgeLM)     // Tests boundaries (red-teaming)
UserPersonas.offTopicUser(judgeLM)        // Goes on tangents
```

  </TabItem>
</Tabs>

Need a character that is not in the list? Build your own with `UserPersonas.custom`. Pass the judge, a one-line persona, and the behavior rules.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
SimulatedUser user = UserPersonas.custom(
    judgeLM,
    "elderly user unfamiliar with technology",
    """
    - Use simple language
    - Ask about basic terminology
    - Express confusion about technical steps
    - Need reassurance
    """
);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val user: SimulatedUser = llmUser(judgeLM) {
    persona = "elderly user unfamiliar with technology"
    behaviorGuidelines = """
        - Use simple language
        - Ask about basic terminology
        - Express confusion about technical steps
        - Need reassurance
    """
}
```

  </TabItem>
</Tabs>

### Conversation Simulator

`ConversationSimulator` runs the chat. It alternates between the simulated user and your app until it hits `maxTurns` or your stopping condition. Each option is commented below.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ConversationSimulator simulator = ConversationSimulator.builder()
    .simulatedUser(user)
    .application(myApp)
    .maxTurns(10)                              // Limit conversation length
    .scenario("Product return request")        // Context for the user
    .initialMessage("I want to return...")     // First user message
    .stoppingCondition(trajectory -> {         // Optional early termination
        Message last = trajectory.lastAssistantMessage();
        return last != null && last.content().contains("goodbye");
    })
    .build();

ConversationTrajectory trajectory = simulator.simulate();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val simulator = simulator {
    simulatedUser = user
    application = myApp
    maxTurns = 10                              // Limit conversation length
    scenario = "Product return request"        // Context for the user
    initialMessage = "I want to return..."     // First user message
    stoppingCondition = { trajectory ->         // Optional early termination
        val last = trajectory.lastAssistantMessage()
        last != null && last.content().contains("goodbye")
    }
}

val trajectory = simulator.simulate()
```

  </TabItem>
</Tabs>

To run the chat off the calling thread, use `simulateAsync` instead of `simulate`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
CompletableFuture<ConversationTrajectory> future = simulator.simulateAsync();
// ... do other work ...
ConversationTrajectory trajectory = future.get();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val trajectory: ConversationTrajectory = simulator.simulateAsync().await()
```

  </TabItem>
</Tabs>

### Wrapping Your Application

The simulator needs to call your app each turn. Implement `ConversationalApplication`. It takes the conversation so far and returns the assistant's next reply.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
@FunctionalInterface
public interface ConversationalApplication {
    Message respond(ConversationTrajectory trajectory);
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
fun interface ConversationalApplication {
    fun respond(trajectory: ConversationTrajectory): Message
}
```

  </TabItem>
</Tabs>

Inside `respond`, convert the trajectory to your framework's message type, call your model, and wrap the reply in `Message.assistant(...)`. Here is how to do that with Spring AI.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ConversationalApplication app = trajectory -> {
    // Convert trajectory to Spring AI messages
    List<org.springframework.ai.chat.messages.Message> messages = trajectory.messages().stream()
        .map(m -> switch (m.role()) {
            case USER -> new UserMessage(m.content());
            case ASSISTANT -> new AssistantMessage(m.content());
            case SYSTEM -> new SystemMessage(m.content());
        })
        .toList();

    String response = chatClient.prompt()
        .messages(messages)
        .call()
        .content();

    return Message.assistant(response);
};
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val app: ConversationalApplication = ConversationalApplication { trajectory ->
    // Convert trajectory to Spring AI messages
    val messages = trajectory.messages()
        .map { m ->
            when (m.role()) {
                Message.Role.USER -> UserMessage(m.content())
                Message.Role.ASSISTANT -> AssistantMessage(m.content())
                Message.Role.SYSTEM -> SystemMessage(m.content())
            }
        }

    val response = chatClient.prompt()
        .messages(messages)
        .call()
        .content()

    Message.assistant(response)
}
```

  </TabItem>
</Tabs>

The same pattern works with LangChain4j. Map the roles to LangChain4j message types and call your `chatModel`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ConversationalApplication app = trajectory -> {
    // Convert trajectory to LangChain4j messages
    List<ChatMessage> messages = trajectory.messages().stream()
        .map(m -> switch (m.role()) {
            case USER -> new UserMessage(m.content());
            case ASSISTANT -> new AiMessage(m.content());
            case SYSTEM -> new SystemMessage(m.content());
        })
        .toList();

    String response = chatModel.chat(messages);
    return Message.assistant(response);
};
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val app: ConversationalApplication = ConversationalApplication { trajectory ->
    // Convert trajectory to LangChain4j messages
    val messages = trajectory.messages()
        .map { m ->
            when (m.role()) {
                Message.Role.USER -> UserMessage(m.content())
                Message.Role.ASSISTANT -> AiMessage(m.content())
                Message.Role.SYSTEM -> SystemMessage(m.content())
            }
        }

    val response = chatModel.chat(messages)
    Message.assistant(response)
}
```

  </TabItem>
</Tabs>

## Trajectory Evaluation

Once you have a trajectory, `TrajectoryEvaluator` grades it. It sends the whole conversation to the judge LLM and scores it against the criteria you pick. Set a `threshold` to decide pass or fail.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
    .name("Support Quality")
    .threshold(0.7)
    .judge(judgeLM)
    .criteria(List.of(
        TrajectoryEvaluationCriteria.userSatisfaction(),
        TrajectoryEvaluationCriteria.goalCompletion(),
        TrajectoryEvaluationCriteria.professionalTone()
    ))
    .aggregationStrategy(AggregationStrategy.WEIGHTED_MEAN)
    .includePerCriterionScores(true)
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val evaluator = trajectoryEvaluator(judgeLM) {
    name = "Support Quality"
    threshold = 0.7
    criteria(listOf(
            TrajectoryEvaluationCriteria.userSatisfaction(),
            TrajectoryEvaluationCriteria.goalCompletion(),
            TrajectoryEvaluationCriteria.professionalTone()
    ))
    aggregationStrategy = AggregationStrategy.WEIGHTED_MEAN
    includePerCriterionScores = true
}
```

  </TabItem>
</Tabs>

### Evaluation Criteria

Each criterion is one thing the judge checks. An `EvaluationCriterion` has a name, a description of what to look for, and a weight. Raise the weight to make a criterion count more in the final score.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
EvaluationCriterion criterion = new EvaluationCriterion(
    "Response Time Awareness",
    "Evaluate if the assistant acknowledged and respected the user's time constraints",
    1.5  // Higher weight
);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val criterion = EvaluationCriterion(
    "Response Time Awareness",
    "Evaluate if the assistant acknowledged and respected the user's time constraints",
    1.5  // Higher weight
)
```

  </TabItem>
</Tabs>

You do not have to write your own. `TrajectoryEvaluationCriteria` has ready-made criteria grouped by what they check.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
// Core quality
TrajectoryEvaluationCriteria.userSatisfaction()     // Was the user satisfied?
TrajectoryEvaluationCriteria.goalCompletion()       // Was the goal achieved?
TrajectoryEvaluationCriteria.conversationQuality()  // Natural flow and coherence

// Professional quality
TrajectoryEvaluationCriteria.responseRelevance()    // On-topic responses
TrajectoryEvaluationCriteria.professionalTone()     // Appropriate demeanor
TrajectoryEvaluationCriteria.problemResolution()    // Issues resolved

// Information quality
TrajectoryEvaluationCriteria.informationAccuracy()  // Factually correct
TrajectoryEvaluationCriteria.clarity()              // Easy to understand
TrajectoryEvaluationCriteria.helpfulness()          // Genuinely helpful

// Behavioral
TrajectoryEvaluationCriteria.consistency()          // No contradictions
TrajectoryEvaluationCriteria.safety()               // Appropriate boundaries
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
// Core quality
TrajectoryEvaluationCriteria.userSatisfaction()     // Was the user satisfied?
TrajectoryEvaluationCriteria.goalCompletion()       // Was the goal achieved?
TrajectoryEvaluationCriteria.conversationQuality()  // Natural flow and coherence

// Professional quality
TrajectoryEvaluationCriteria.responseRelevance()    // On-topic responses
TrajectoryEvaluationCriteria.professionalTone()     // Appropriate demeanor
TrajectoryEvaluationCriteria.problemResolution()    // Issues resolved

// Information quality
TrajectoryEvaluationCriteria.informationAccuracy()  // Factually correct
TrajectoryEvaluationCriteria.clarity()              // Easy to understand
TrajectoryEvaluationCriteria.helpfulness()          // Genuinely helpful

// Behavioral
TrajectoryEvaluationCriteria.consistency()          // No contradictions
TrajectoryEvaluationCriteria.safety()               // Appropriate boundaries
```

  </TabItem>
</Tabs>

### Aggregation Strategies

The judge scores each criterion. The aggregation strategy decides how those scores combine into one number.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
AggregationStrategy.MEAN           // Simple average
AggregationStrategy.WEIGHTED_MEAN  // Weighted by criterion weights
AggregationStrategy.MIN            // Strictest: lowest score wins
AggregationStrategy.MAX            // Most lenient: highest score wins
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
AggregationStrategy.MEAN           // Simple average
AggregationStrategy.WEIGHTED_MEAN  // Weighted by criterion weights
AggregationStrategy.MIN            // Strictest: lowest score wins
AggregationStrategy.MAX            // Most lenient: highest score wins
```

  </TabItem>
</Tabs>

### Evaluation Results

`evaluate` returns an `EvalResult` with the overall score, a pass flag, and metadata. When you set `includePerCriterionScores(true)`, the metadata holds the score and reason for every criterion under `criterionScores`. Read it like this.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
EvalResult result = evaluator.evaluate(testCase);

System.out.println("Overall Score: " + result.score());
System.out.println("Passed: " + result.success());
System.out.println("Turn Count: " + result.metadata().get("turnCount"));

// Per-criterion breakdown
Map<String, Object> criterionScores =
    (Map<String, Object>) result.metadata().get("criterionScores");
criterionScores.forEach((name, details) -> {
    Map<String, Object> d = (Map<String, Object>) details;
    System.out.println(name + ": " + d.get("score") + " - " + d.get("reason"));
});
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val result = evaluator.evaluate(testCase)

println("Overall Score: ${result.score()}")
println("Passed: ${result.success()}")
println("Turn Count: ${result.metadata()["turnCount"]}")

// Per-criterion breakdown
val criterionScores = result.metadata()["criterionScores"] as Map<String, Any>
criterionScores.forEach { (name, details) ->
    val d = details as Map<String, Any>
    println("$name: ${d["score"]} - ${d["reason"]}")
}
```

  </TabItem>
</Tabs>

## Complete Example

This puts every step together: a runnable `main` that tests a customer service chatbot end to end. Swap `myChatbot` and `openAiClient` for your own.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
public class CustomerServiceEvaluation {

    public static void main(String[] args) {
        // Setup judge LLM
        JudgeLM judgeLM = prompt -> openAiClient.chat(prompt);

        // Create simulated user with specific persona
        SimulatedUser user = LLMSimulatedUser.builder()
            .judge(judgeLM)
            .persona("frustrated customer who received a damaged product")
            .behaviorGuidelines("""
                - Express disappointment about the damaged item
                - Request either replacement or refund
                - Be firm but not abusive
                - Mention you've been a loyal customer
                """)
            .fixedResponses(List.of(
                "I just received my order and the item is completely damaged!"
            ))
            .build();

        // Wrap the chatbot being tested
        ConversationalApplication chatbot = trajectory -> {
            // Your chatbot implementation here
            String response = myChatbot.respond(trajectory.toText());
            return Message.assistant(response);
        };

        // Run the simulation
        ConversationTrajectory trajectory = ConversationSimulator.builder()
            .simulatedUser(user)
            .application(chatbot)
            .maxTurns(6)
            .scenario("Customer received damaged product and wants resolution")
            .build()
            .simulate();

        // Print the conversation
        System.out.println("=== Conversation ===");
        System.out.println(trajectory.toText());

        // Evaluate
        TrajectoryEvaluator evaluator = TrajectoryEvaluator.builder()
            .name("Customer Service Quality")
            .threshold(0.7)
            .judge(judgeLM)
            .criteria(List.of(
                TrajectoryEvaluationCriteria.userSatisfaction(),
                TrajectoryEvaluationCriteria.problemResolution(),
                TrajectoryEvaluationCriteria.professionalTone(),
                TrajectoryEvaluationCriteria.helpfulness()
            ))
            .aggregationStrategy(AggregationStrategy.WEIGHTED_MEAN)
            .build();

        EvalTestCase testCase = EvalTestCase.builder()
            .actualOutput("trajectory", trajectory)
            .build();

        EvalResult result = evaluator.evaluate(testCase);

        // Print the results
        System.out.println("\n=== Evaluation Results ===");
        System.out.println("Overall Score: " + String.format("%.2f", result.score()));
        System.out.println("Passed: " + result.success());
        System.out.println("Reason: " + result.reason());
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
object CustomerServiceEvaluation {

    @JvmStatic
    fun main(args: Array<String>) {
        // Setup judge LLM
        val judgeLM = JudgeLM { prompt -> openAiClient.chat(prompt) }

        // Create simulated user with specific persona
        val user: SimulatedUser = llmUser(judgeLM) {
            persona = "frustrated customer who received a damaged product"
            behaviorGuidelines = """
                - Express disappointment about the damaged item
                - Request either replacement or refund
                - Be firm but not abusive
                - Mention you've been a loyal customer
            """
            fixedResponses(listOf("I just received my order and the item is completely damaged!"))
        }

        // Wrap the chatbot being tested
        val chatbot: ConversationalApplication = ConversationalApplication { trajectory ->
            // Your chatbot implementation here
            val response = myChatbot.respond(trajectory.toText())
            Message.assistant(response)
        }

        // Run the simulation
        val trajectory = simulator {
            simulatedUser = user
            application = chatbot
            maxTurns = 6
            scenario = "Customer received damaged product and wants resolution"
        }.simulate()

        // Print the conversation
        println("=== Conversation ===")
        println(trajectory.toText())

        // Evaluate
        val evaluator = trajectoryEvaluator(judgeLM) {
            name = "Customer Service Quality"
            threshold = 0.7
            criteria(listOf(
                    TrajectoryEvaluationCriteria.userSatisfaction(),
                    TrajectoryEvaluationCriteria.problemResolution(),
                    TrajectoryEvaluationCriteria.professionalTone(),
                    TrajectoryEvaluationCriteria.helpfulness()
            ))
            aggregationStrategy = AggregationStrategy.WEIGHTED_MEAN
        }

        val testCase = EvalTestCase(
            actualOutputs = mapOf("trajectory" to trajectory)
        )

        val result = evaluator.evaluate(testCase)

        // Print the results
        println("\n=== Evaluation Results ===")
        println("Overall Score: ${"%.2f".format(result.score())}")
        println("Passed: ${result.success()}")
        println("Reason: ${result.reason()}")
    }
}
```

  </TabItem>
</Tabs>

## Generating Conversation Goldens

Writing multi-turn test data by hand is slow. `GoldenGenerator` runs a set of scenario seeds through the simulator and turns each resulting conversation into a dataset example, so you can generate a suite once, commit it, and replay it in CI.

A seed is one conversation to synthesize. It is either **scripted** (a fixed list of user turns, replayed verbatim, with no LLM on the user side) or **persona-driven** (a factory that receives the generator's `JudgeLM` and returns a simulated user). A scripted seed needs no judge, so what it costs comes down to your application: a deterministic stub keeps the whole run offline and repeatable, while an LLM-backed application still answers every turn and still bills you for it.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
ScenarioSeed refund = ScenarioSeed.builder()
    .scenario("Refund for a broken product")
    .userTurns(List.of("My blender arrived broken and I want a refund", "The order number is #123"))
    .expectedOutcome("The agent asks for the order number and then issues a refund")
    .build();

ScenarioSeed escalation = ScenarioSeed.builder()
    .scenario("Angry customer escalates")
    .initialMessage("This product broke on day one!")
    .personaFactory(UserPersonas::aggressiveCustomer)
    .maxTurns(6)
    .build();

GoldenGenerator generator = GoldenGenerator.builder()
    .application(app)
    .judge(judgeLM)          // only needed for persona-driven seeds
    .name("support-goldens")
    .seed(refund)
    .seed(escalation)
    .build();

generator.write(Path.of("src/test/resources/datasets/support-goldens.json"));
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val generator = goldenGenerator {
    application = app
    judge = judgeLM          // only needed for persona-driven seeds
    name = "support-goldens"

    seed {
        scenario = "Refund for a broken product"
        userTurns(listOf("My blender arrived broken and I want a refund", "The order number is #123"))
        expectedOutcome = "The agent asks for the order number and then issues a refund"
    }

    seed {
        scenario = "Angry customer escalates"
        initialMessage = "This product broke on day one!"
        personaFactory = UserPersonas::aggressiveCustomer
        maxTurns = 6
    }
}

generator.write(Path.of("src/test/resources/datasets/support-goldens.json"))
```

  </TabItem>
</Tabs>

### What a Golden Looks Like

Each seed produces one example, in seed order:

| Field | Value |
| --- | --- |
| `inputs["input"]` | the rendered transcript of the whole conversation |
| `expectedOutputs["output"]` | the application's last reply, **for scripted seeds only**, when the run produced one |
| `metadata` | `scenario`, `turnCount`, `expectedOutcome` when set, your own seed metadata, and `error` plus `errorSource` if the run failed |
| `id` | `golden-0`, `golden-1`, and so on |

A scripted seed records a baseline of what your application says today, so the default golden answer is useful. A persona-driven seed gets **no** default `output`: grading an application against its own reply proves nothing. Set the reference answer yourself with `expectedOutput("output", ...)` on the seed, which also overrides the scripted default.

`expectedOutcome` is a natural-language completion criterion. It never stops the simulation; it rides along in metadata so a judge (for example `TaskCompletionEvaluator`) or a human reviewer can check whether the conversation actually got there.

Two details worth knowing: a scripted seed stops at its last user turn even when `maxTurns` is higher, and setting `initialMessage` on a scripted seed replaces its first turn, so `userTurns.get(0)` is never sent.

### Replaying the Goldens

`toJson()` and `toJsonl()` emit the same dataset shape `Dataset` parses, so a generated file feeds `@DatasetSource` directly. Never send `inputs["input"]` back to your application as a prompt: it is the whole transcript, assistant replies included, so you would be handing the model the answer it is being graded on. Grading the transcript as it stands checks the recording, with `expectedOutcome` riding along in metadata as the task for a judge:

```java
@ParameterizedTest
@DatasetSource("classpath:datasets/support-goldens.json")
void replaysGoldens(Example example) {
    EvalTestCase testCase = EvalTestCase.builder()
        .input(example.input())
        .metadata("tasks", List.of(example.metadata().get("expectedOutcome")))
        .build();

    Assertions.assertEval(testCase, TaskCompletionEvaluator.builder().judge(judgeLM).build());
}
```

To gate your application rather than the recording, replay the transcript's `USER:` turns through `ConversationSimulator` and grade the conversation that comes back. [Generate conversation test data](../tutorials/generate-conversation-test-data) walks through that end to end.

Generation is stateless: every call to `generate()`, including the one inside `write(...)`, re-runs the seeds, so persona-driven seeds produce a fresh conversation each time. Write the file once and commit it when you want a stable suite. Key order in the written file is stable, so a regenerated file diffs only where the text itself changed, and the scripted user turns never do.

See [`MultiTurnGoldenGenerationExample.java`](https://github.com/dokimos-dev/dokimos/blob/master/dokimos-examples/src/main/java/dev/dokimos/examples/conversation/MultiTurnGoldenGenerationExample.java) for a complete runnable version.

## Best Practices

### Choose appropriate personas

Pick the persona that matches what you are testing:

- Testing how it holds up under pressure? Use `adversarialUser` or `aggressiveCustomer`.
- Testing clarity? Use `confusedUser` or `noviceUser`.
- Testing happy paths? Use `satisfiedCustomer`.

### Set realistic turn limits

Most real conversations resolve in 5 to 10 turns. A `maxTurns` that is too high wastes API calls. One that is too low cuts the chat off before it resolves.

### Use stopping conditions for efficiency

Stop the chat as soon as the goal is met, so you do not pay for extra turns.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
.stoppingCondition(trajectory -> {
    Message last = trajectory.lastAssistantMessage();
    return last != null && (
        last.content().contains("Is there anything else") ||
        last.content().contains("Have a great day")
    );
})
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
.stoppingCondition { trajectory ->
    val last = trajectory.lastAssistantMessage()
    last != null && (
        last.content().contains("Is there anything else") ||
        last.content().contains("Have a great day")
    )
}
```

  </TabItem>
</Tabs>

### Choose the right aggregation strategy

- **WEIGHTED_MEAN**: good default. Lets you prioritize criteria by weight.
- **MIN**: every criterion must pass. Use it as a strict quality gate.
- **MEAN**: simple equal weighting.
- **MAX**: lenient. Use it sparingly.

### Test multiple scenarios

Do not test one user type. Loop over several personas so you catch problems each one exposes.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
List<SimulatedUser> personas = List.of(
    UserPersonas.aggressiveCustomer(judgeLM),
    UserPersonas.confusedUser(judgeLM),
    UserPersonas.satisfiedCustomer(judgeLM)
);

for (SimulatedUser user : personas) {
    ConversationTrajectory trajectory = ConversationSimulator.builder()
        .simulatedUser(user)
        .application(app)
        .maxTurns(8)
        .build()
        .simulate();

    EvalResult result = evaluator.evaluate(
        EvalTestCase.builder()
            .actualOutput("trajectory", trajectory)
            .build()
    );

    System.out.println(user + ": " + result.score());
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val personas = listOf(
    UserPersonas.aggressiveCustomer(judgeLM),
    UserPersonas.confusedUser(judgeLM),
    UserPersonas.satisfiedCustomer(judgeLM)
)

personas.forEach { user ->
    val trajectory = simulator {
        simulatedUser = user
        application = app
        maxTurns = 8
    }.simulate()

    val result = evaluator.evaluate(
        EvalTestCase(
            actualOutputs = mapOf("trajectory" to trajectory)
        )
    )

    println("$user: ${result.score()}")
}
```

  </TabItem>
</Tabs>

### Debug with trajectory JSON

When a test fails, print the full conversation to see what the assistant actually said.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
System.out.println(trajectory.toJson());  // Pretty-printed JSON
System.out.println(trajectory.toText());  // Human-readable transcript
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
println(trajectory.toJson())  // Pretty-printed JSON
println(trajectory.toText())  // Human-readable transcript
```

  </TabItem>
</Tabs>
