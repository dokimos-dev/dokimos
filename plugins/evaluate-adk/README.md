# evaluate-adk

Claude Code plugin for evaluating [Google Agent Development Kit (ADK) for Java](https://github.com/google/adk-java) (`dokimos-adk-java`) and [Google ADK for Kotlin](https://github.com/google/adk-kotlin) (`dokimos-adk-kotlin`) agents with Dokimos.

## Usage

```bash
/plugin install evaluate-adk@dokimos
```

Covers:
- Turning ADK `BaseLlm` / `Model` (`Gemini`), `Runner`, or `BaseAgent` (`LlmAgent`) into a `JudgeLM`
- Capturing `AgentTrace`, `ToolCall`s, and full `ToolDefinition` schemas via `AdkSupport` or `AdkTraceCollector`
- Evaluating RAG pipelines using `AdkSupport.ragTask` and `GroundingMetadata` / tool context extraction
- Multi-turn `ConversationTrajectory` and `ConversationSimulator` integration
- Token usage, latency, and cost tracking with `CallMetrics` and `PriceTable`
