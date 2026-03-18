package dev.dokimos.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dokimos.mcp.store.JsonResultStore;
import dev.dokimos.mcp.store.ResultStore;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP server that exposes dokimos evaluation tools over stdio transport.
 *
 * <p>Provides four tools: run_evaluation, list_experiments, compare_runs, get_failing_queries.
 * Designed for use with Claude Desktop or any MCP client.
 */
public class DokimosMcpServer {

    private static final Logger log = LoggerFactory.getLogger(DokimosMcpServer.class);

    public static void main(String[] args) {
        ObjectMapper json = new ObjectMapper();
        json.registerModule(new JavaTimeModule());

        ResultStore store = new JsonResultStore();
        ToolHandlers handlers = new ToolHandlers(store, json);

        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("dokimos-mcp-server", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .toolCall(runEvaluationTool(), (exchange, request) -> handlers.handleRunEvaluation(request.arguments()))
                .toolCall(
                        listExperimentsTool(),
                        (exchange, request) -> handlers.handleListExperiments(request.arguments()))
                .toolCall(compareRunsTool(), (exchange, request) -> handlers.handleCompareRuns(request.arguments()))
                .toolCall(
                        getFailingQueriesTool(),
                        (exchange, request) -> handlers.handleGetFailingQueries(request.arguments()))
                .build();

        log.info("dokimos MCP server started (stdio transport)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down dokimos MCP server");
            server.close();
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Tool runEvaluationTool() {
        return Tool.builder()
                .name("run_evaluation")
                .description(
                        "Run a dokimos LLM evaluation. Loads a dataset, calls the specified model for each example, "
                                + "evaluates the outputs, and returns summary metrics with a run ID for future reference.")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of(
                                "dataset_path",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "Path to the dataset file (JSON, CSV, or JSONL)"),
                                "model",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "OpenAI model name (default: gpt-4o-mini)"),
                                "temperature",
                                        Map.of(
                                                "type",
                                                "number",
                                                "description",
                                                "Sampling temperature, 0.0 to 2.0 (default: 0.0)"),
                                "evaluator",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "Evaluator type: exact_match or llm_judge (default: exact_match)"),
                                "criteria",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "Evaluation criteria for llm_judge evaluator"),
                                "threshold",
                                        Map.of(
                                                "type",
                                                "number",
                                                "description",
                                                "Score threshold for pass/fail (default: 0.7)"),
                                "experiment_name",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "Name for this experiment (default: mcp-evaluation)")),
                        List.of("dataset_path"),
                        null,
                        null,
                        null))
                .build();
    }

    private static Tool listExperimentsTool() {
        return Tool.builder()
                .name("list_experiments")
                .description(
                        "List past dokimos evaluation runs. Returns run IDs, timestamps, dataset names, and summary metrics.")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of(
                                "limit",
                                        Map.of(
                                                "type",
                                                "integer",
                                                "description",
                                                "Maximum number of runs to return (default: 20)"),
                                "dataset_name", Map.of("type", "string", "description", "Filter by dataset name")),
                        List.of(),
                        null,
                        null,
                        null))
                .build();
    }

    private static Tool compareRunsTool() {
        return Tool.builder()
                .name("compare_runs")
                .description("Compare two evaluation runs side by side. Shows metric deltas and flags regressions.")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of(
                                "run_id_a", Map.of("type", "string", "description", "First run ID (baseline)"),
                                "run_id_b", Map.of("type", "string", "description", "Second run ID (comparison)")),
                        List.of("run_id_a", "run_id_b"),
                        null,
                        null,
                        null))
                .build();
    }

    private static Tool getFailingQueriesTool() {
        return Tool.builder()
                .name("get_failing_queries")
                .description(
                        "Get failing queries from an evaluation run. Returns examples where evaluator scores fell below the threshold.")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of(
                                "run_id", Map.of("type", "string", "description", "Run ID to inspect"),
                                "threshold",
                                        Map.of(
                                                "type",
                                                "number",
                                                "description",
                                                "Score threshold below which queries are failing (default: 0.5)")),
                        List.of("run_id"),
                        null,
                        null,
                        null))
                .build();
    }
}
