package io.jclaw.cronmanager.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jclaw.core.mcp.McpToolDefinition;
import io.jclaw.core.mcp.McpToolProvider;
import io.jclaw.core.mcp.McpToolResult;
import io.jclaw.core.model.CronJob;
import io.jclaw.core.tenant.TenantContext;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.cronmanager.CronJobManagerService;
import io.jclaw.cronmanager.model.CronExecutionRecord;
import io.jclaw.cronmanager.model.CronJobDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tool provider exposing cron job management tools.
 * Server name: {@code cron-manager}, with 8 tools for CRUD, execution, and history.
 */
public class CronManagerMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(CronManagerMcpToolProvider.class);
    private static final String SERVER_NAME = "cron-manager";
    private static final String SERVER_DESCRIPTION = "Scheduled job management — create, list, pause, resume, run, and monitor cron jobs";

    private final CronJobManagerService managerService;
    private final ObjectMapper objectMapper;

    public CronManagerMcpToolProvider(CronJobManagerService managerService) {
        this.managerService = managerService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return SERVER_DESCRIPTION;
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("create_job",
                        "Create a new scheduled cron job",
                        CREATE_JOB_SCHEMA),
                new McpToolDefinition("list_jobs",
                        "List all cron jobs",
                        EMPTY_SCHEMA),
                new McpToolDefinition("get_job",
                        "Get details of a specific cron job",
                        JOB_ID_SCHEMA),
                new McpToolDefinition("delete_job",
                        "Delete a cron job",
                        JOB_ID_SCHEMA),
                new McpToolDefinition("run_job_now",
                        "Execute a cron job immediately",
                        JOB_ID_SCHEMA),
                new McpToolDefinition("get_job_history",
                        "Get execution history for a cron job",
                        HISTORY_SCHEMA),
                new McpToolDefinition("pause_job",
                        "Pause (disable) a cron job",
                        JOB_ID_SCHEMA),
                new McpToolDefinition("resume_job",
                        "Resume (re-enable) a paused cron job",
                        JOB_ID_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "create_job" -> handleCreateJob(args);
                case "list_jobs" -> handleListJobs();
                case "get_job" -> handleGetJob(args);
                case "delete_job" -> handleDeleteJob(args);
                case "run_job_now" -> handleRunJobNow(args);
                case "get_job_history" -> handleGetJobHistory(args);
                case "pause_job" -> handlePauseJob(args);
                case "resume_job" -> handleResumeJob(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private McpToolResult handleCreateJob(Map<String, Object> args) throws JsonProcessingException {
        String name = (String) args.getOrDefault("name", "Unnamed Job");
        String schedule = (String) args.get("schedule");
        String prompt = (String) args.get("prompt");
        String agentId = (String) args.getOrDefault("agentId", "default");
        String timezone = (String) args.getOrDefault("timezone", "UTC");
        boolean enabled = (Boolean) args.getOrDefault("enabled", true);
        String profileStr = (String) args.getOrDefault("toolProfile", "MINIMAL");
        ToolProfile toolProfile = ToolProfile.valueOf(profileStr.toUpperCase());

        if (schedule == null || prompt == null) {
            return McpToolResult.error("'schedule' and 'prompt' are required");
        }

        CronJob cronJob = new CronJob(
                UUID.randomUUID().toString(), name, agentId, schedule, timezone,
                prompt, null, null, enabled, null, null);

        CronJobDefinition definition = new CronJobDefinition(
                cronJob, null, null, null, toolProfile, List.of());

        CronJobDefinition created = managerService.createJob(definition);
        return McpToolResult.success(toJson(created));
    }

    private McpToolResult handleListJobs() throws JsonProcessingException {
        List<CronJobDefinition> jobs = managerService.listJobs();
        return McpToolResult.success(toJson(jobs));
    }

    private McpToolResult handleGetJob(Map<String, Object> args) throws JsonProcessingException {
        String jobId = requireString(args, "jobId");
        return managerService.getJob(jobId)
                .map(def -> {
                    try {
                        return McpToolResult.success(toJson(def));
                    } catch (JsonProcessingException e) {
                        return McpToolResult.error("Serialization failed: " + e.getMessage());
                    }
                })
                .orElse(McpToolResult.error("Job not found: " + jobId));
    }

    private McpToolResult handleDeleteJob(Map<String, Object> args) {
        String jobId = requireString(args, "jobId");
        boolean deleted = managerService.deleteJob(jobId);
        return deleted
                ? McpToolResult.success("{\"deleted\": true, \"jobId\": \"" + jobId + "\"}")
                : McpToolResult.error("Job not found: " + jobId);
    }

    private McpToolResult handleRunJobNow(Map<String, Object> args) {
        String jobId = requireString(args, "jobId");
        String runId = managerService.runNow(jobId);
        return McpToolResult.success("{\"launched\": true, \"jobId\": \"" + jobId + "\", \"runId\": \"" + runId + "\"}");
    }

    private McpToolResult handleGetJobHistory(Map<String, Object> args) throws JsonProcessingException {
        String jobId = requireString(args, "jobId");
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;
        List<CronExecutionRecord> history = managerService.getJobHistory(jobId, limit);
        return McpToolResult.success(toJson(history));
    }

    private McpToolResult handlePauseJob(Map<String, Object> args) {
        String jobId = requireString(args, "jobId");
        boolean paused = managerService.pauseJob(jobId);
        return paused
                ? McpToolResult.success("{\"paused\": true, \"jobId\": \"" + jobId + "\"}")
                : McpToolResult.error("Job not found: " + jobId);
    }

    private McpToolResult handleResumeJob(Map<String, Object> args) {
        String jobId = requireString(args, "jobId");
        boolean resumed = managerService.resumeJob(jobId);
        return resumed
                ? McpToolResult.success("{\"resumed\": true, \"jobId\": \"" + jobId + "\"}")
                : McpToolResult.error("Job not found: " + jobId);
    }

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return value.toString();
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    // --- JSON Schema constants ---

    private static final String EMPTY_SCHEMA = """
            {"type": "object", "properties": {}}""";

    private static final String JOB_ID_SCHEMA = """
            {"type": "object", "properties": {"jobId": {"type": "string", "description": "The cron job ID"}}, "required": ["jobId"]}""";

    private static final String CREATE_JOB_SCHEMA = """
            {"type": "object", "properties": {\
            "name": {"type": "string", "description": "Human-readable job name"},\
            "schedule": {"type": "string", "description": "5-field cron expression (minute hour day month day-of-week)"},\
            "prompt": {"type": "string", "description": "The prompt to send to the agent"},\
            "agentId": {"type": "string", "description": "Agent ID (default: 'default')"},\
            "timezone": {"type": "string", "description": "Timezone for schedule (default: 'UTC')"},\
            "enabled": {"type": "boolean", "description": "Whether job is enabled (default: true)"},\
            "toolProfile": {"type": "string", "description": "Tool profile: MINIMAL, CODING, MESSAGING, FULL (default: MINIMAL)"}\
            }, "required": ["schedule", "prompt"]}""";

    private static final String HISTORY_SCHEMA = """
            {"type": "object", "properties": {\
            "jobId": {"type": "string", "description": "The cron job ID"},\
            "limit": {"type": "integer", "description": "Max records to return (default: 20)"}\
            }, "required": ["jobId"]}""";
}
