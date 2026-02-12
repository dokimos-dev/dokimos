---
sidebar_position: 5
---

# Multi-Turn Conversations

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Evaluating multi-turn conversations is more complex than single-turn interactions. You need to test how your AI system handles back-and-forth exchanges, maintains context, and achieves user goals over multiple turns.

Dokimos provides a complete system for simulating and evaluating multi-turn conversations:

- **Simulated Users**: LLM-based users that play different roles (angry customers, confused users, technical experts)
- **Conversation Simulator**: Orchestrates turn-taking between your app and the simulated user
- **Trajectory Evaluator**: Judges the entire conversation using LLM-as-judge patterns

## Quick Example

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

// 3. Run simulation
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

// 3. Run simulation
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
    criteria(
            TrajectoryEvaluationCriteria.userSatisfaction(),
            TrajectoryEvaluationCriteria.problemResolution()
    )
}
    .evaluate(
        EvalTestCase(
            actualOutputs = mapOf("trajectory" to trajectory)
        )
    )
```

  </TabItem>
</Tabs>

## Core Concepts

### Messages and Trajectories

A conversation is a sequence of messages:

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

A `ConversationTrajectory` holds the complete conversation history:

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

// Helpful methods
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

// Helpful methods
trajectory.turnCount()           // Number of complete turns
trajectory.userMessages()        // All user messages
trajectory.assistantMessages()   // All assistant messages
trajectory.lastMessage()         // Most recent message
trajectory.toJson()              // JSON for debugging
trajectory.toText()              // Plain text transcript
```

  </TabItem>
</Tabs>

### Simulated Users

The `SimulatedUser` interface generates contextually appropriate user messages:

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

The `LLMSimulatedUser` uses an LLM to generate realistic user behavior:

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

You can also provide fixed initial messages:

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
    fixedResponses(
            "I ordered a blue shirt but received a red one!",
            "I want a full refund, not a replacement"
    )
}
```

  </TabItem>
</Tabs>

The first two turns will use fixed responses; after that, the LLM generates contextual replies.

#### Pre-Built Personas

Dokimos includes ready-to-use personas for common testing scenarios:

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

Or create custom personas:

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

The `ConversationSimulator` orchestrates turn-taking:

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

**Async simulation** is also supported:

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

Your application must implement `ConversationalApplication`:

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

**Example with Spring AI:**

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

**Example with LangChain4j:**

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

The `TrajectoryEvaluator` assesses the entire conversation using LLM-as-judge:

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
    criteria(
            TrajectoryEvaluationCriteria.userSatisfaction(),
            TrajectoryEvaluationCriteria.goalCompletion(),
            TrajectoryEvaluationCriteria.professionalTone()
    )
    aggregationStrategy = AggregationStrategy.WEIGHTED_MEAN
    includePerCriterionScores = true
}
```

  </TabItem>
</Tabs>

### Evaluation Criteria

Each criterion defines what aspect to evaluate:

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

**Pre-built criteria:**

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

Control how multiple criteria scores combine:

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

Results include per-criterion scores in metadata:

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

Here's a full example testing a customer service chatbot:

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

        // Run simulation
        ConversationTrajectory trajectory = ConversationSimulator.builder()
            .simulatedUser(user)
            .application(chatbot)
            .maxTurns(6)
            .scenario("Customer received damaged product and wants resolution")
            .build()
            .simulate();

        // Print conversation
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

        // Print results
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

        // Run simulation
        val trajectory = simulator {
            simulatedUser = user
            application = chatbot
            maxTurns = 6
            scenario = "Customer received damaged product and wants resolution"
        }.simulate()

        // Print conversation
        println("=== Conversation ===")
        println(trajectory.toText())

        // Evaluate
        val evaluator = trajectoryEvaluator(judgeLM) {
            name = "Customer Service Quality"
            threshold = 0.7
            criteria(
                    TrajectoryEvaluationCriteria.userSatisfaction(),
                    TrajectoryEvaluationCriteria.problemResolution(),
                    TrajectoryEvaluationCriteria.professionalTone(),
                    TrajectoryEvaluationCriteria.helpfulness()
            )
            aggregationStrategy = AggregationStrategy.WEIGHTED_MEAN
        }

        val testCase = EvalTestCase(
            actualOutputs = mapOf("trajectory" to trajectory)
        )

        val result = evaluator.evaluate(testCase)

        // Print results
        println("\n=== Evaluation Results ===")
        println("Overall Score: ${"%.2f".format(result.score())}")
        println("Passed: ${result.success()}")
        println("Reason: ${result.reason()}")
    }
}
```

  </TabItem>
</Tabs>

## Best Practices

### Choose appropriate personas

Match the persona to what you're testing:
- Testing robustness? Use `adversarialUser` or `aggressiveCustomer`
- Testing clarity? Use `confusedUser` or `noviceUser`
- Testing happy paths? Use `satisfiedCustomer`

### Set realistic turn limits

Most real conversations resolve in 5-10 turns. Setting `maxTurns` too high wastes resources; too low may cut off before resolution.

### Use stopping conditions for efficiency

End conversations early when the goal is clearly achieved:

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

- **WEIGHTED_MEAN**: Good for most cases, lets you prioritize criteria
- **MIN**: Use when all criteria must pass (strict quality gate)
- **MEAN**: Simple equal weighting
- **MAX**: Lenient, use sparingly

### Test multiple scenarios

Don't just test one user type. Create a test suite covering different personas and scenarios:

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

When tests fail, inspect the full conversation:

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
