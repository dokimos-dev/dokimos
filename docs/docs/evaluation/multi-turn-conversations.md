---
sidebar_position: 5
---

# Multi-Turn Conversations

Evaluating multi-turn conversations is more complex than single-turn interactions. You need to test how your AI system handles back-and-forth exchanges, maintains context, and achieves user goals over multiple turns.

Dokimos provides a complete system for simulating and evaluating multi-turn conversations:

- **Simulated Users**: LLM-based users that play different roles (angry customers, confused users, technical experts)
- **Conversation Simulator**: Orchestrates turn-taking between your app and the simulated user
- **Trajectory Evaluator**: Judges the entire conversation using LLM-as-judge patterns

## Quick Example

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

## Core Concepts

### Messages and Trajectories

A conversation is a sequence of messages:

```java
Message userMsg = Message.user("I need help with my order");
Message assistantMsg = Message.assistant("I'd be happy to help. What's your order number?");
Message systemMsg = Message.system("You are a helpful support agent");
```

A `ConversationTrajectory` holds the complete conversation history:

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

### Simulated Users

The `SimulatedUser` interface generates contextually appropriate user messages:

```java
@FunctionalInterface
public interface SimulatedUser {
    Message generateMessage(ConversationTrajectory trajectory);
}
```

#### LLM-Based Simulated User

The `LLMSimulatedUser` uses an LLM to generate realistic user behavior:

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

You can also provide fixed initial messages:

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

The first two turns will use fixed responses; after that, the LLM generates contextual replies.

#### Pre-Built Personas

Dokimos includes ready-to-use personas for common testing scenarios:

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

Or create custom personas:

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

### Conversation Simulator

The `ConversationSimulator` orchestrates turn-taking:

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

**Async simulation** is also supported:

```java
CompletableFuture<ConversationTrajectory> future = simulator.simulateAsync();
// ... do other work ...
ConversationTrajectory trajectory = future.get();
```

### Wrapping Your Application

Your application must implement `ConversationalApplication`:

```java
@FunctionalInterface
public interface ConversationalApplication {
    Message respond(ConversationTrajectory trajectory);
}
```

**Example with Spring AI:**

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

**Example with LangChain4j:**

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

## Trajectory Evaluation

The `TrajectoryEvaluator` assesses the entire conversation using LLM-as-judge:

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

### Evaluation Criteria

Each criterion defines what aspect to evaluate:

```java
EvaluationCriterion criterion = new EvaluationCriterion(
    "Response Time Awareness",
    "Evaluate if the assistant acknowledged and respected the user's time constraints",
    1.5  // Higher weight
);
```

**Pre-built criteria:**

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

### Aggregation Strategies

Control how multiple criteria scores combine:

```java
AggregationStrategy.MEAN           // Simple average
AggregationStrategy.WEIGHTED_MEAN  // Weighted by criterion weights
AggregationStrategy.MIN            // Strictest: lowest score wins
AggregationStrategy.MAX            // Most lenient: highest score wins
```

### Evaluation Results

Results include per-criterion scores in metadata:

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

## Complete Example

Here's a full example testing a customer service chatbot:

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

```java
.stoppingCondition(trajectory -> {
    Message last = trajectory.lastAssistantMessage();
    return last != null && (
        last.content().contains("Is there anything else") ||
        last.content().contains("Have a great day")
    );
})
```

### Choose the right aggregation strategy

- **WEIGHTED_MEAN**: Good for most cases, lets you prioritize criteria
- **MIN**: Use when all criteria must pass (strict quality gate)
- **MEAN**: Simple equal weighting
- **MAX**: Lenient, use sparingly

### Test multiple scenarios

Don't just test one user type. Create a test suite covering different personas and scenarios:

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

### Debug with trajectory JSON

When tests fail, inspect the full conversation:

```java
System.out.println(trajectory.toJson());  // Pretty-printed JSON
System.out.println(trajectory.toText());  // Human-readable transcript
```
