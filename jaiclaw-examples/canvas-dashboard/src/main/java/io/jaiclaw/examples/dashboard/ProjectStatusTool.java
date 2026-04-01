package io.jaiclaw.examples.dashboard;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;

import java.time.LocalDate;
import java.util.Map;

/**
 * Returns simulated project management data (sprints, tasks, team velocity).
 * Provides realistic-looking data for a richer dashboard demo.
 */
public class ProjectStatusTool implements ToolCallback {

    private static final ToolDefinition DEF = new ToolDefinition(
            "get_project_status",
            "Get project status data including sprint progress, task breakdown by status, "
                    + "team velocity history, and upcoming milestones. Returns JSON.",
            "dashboard"
    );

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        LocalDate today = LocalDate.now();
        LocalDate sprintEnd = today.plusDays(7);

        String json = """
                {
                  "project": {
                    "name": "Phoenix Platform",
                    "version": "2.4.0",
                    "reportDate": "%s"
                  },
                  "sprint": {
                    "name": "Sprint 18",
                    "startDate": "%s",
                    "endDate": "%s",
                    "daysRemaining": 7,
                    "completionPercent": 62
                  },
                  "tasks": {
                    "total": 34,
                    "done": 21,
                    "inProgress": 8,
                    "todo": 3,
                    "blocked": 2
                  },
                  "velocity": {
                    "sprints": ["S13", "S14", "S15", "S16", "S17", "S18"],
                    "planned": [28, 32, 30, 35, 33, 34],
                    "completed": [26, 30, 31, 32, 29, 21]
                  },
                  "team": [
                    {"name": "Alice", "tasksCompleted": 6, "tasksInProgress": 2},
                    {"name": "Bob", "tasksCompleted": 5, "tasksInProgress": 1},
                    {"name": "Carol", "tasksCompleted": 4, "tasksInProgress": 3},
                    {"name": "Dave", "tasksCompleted": 3, "tasksInProgress": 1},
                    {"name": "Eve", "tasksCompleted": 3, "tasksInProgress": 1}
                  ],
                  "milestones": [
                    {"name": "API v2 Launch", "date": "%s", "status": "on-track"},
                    {"name": "Mobile App Beta", "date": "%s", "status": "at-risk"},
                    {"name": "Security Audit", "date": "%s", "status": "completed"}
                  ]
                }""".formatted(
                today,
                today.minusDays(7),
                sprintEnd,
                today.plusDays(14),
                today.plusDays(30),
                today.minusDays(3)
        );

        return new ToolResult.Success(json);
    }
}
