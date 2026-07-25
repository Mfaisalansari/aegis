package com.aegis.example.plugin;

import com.aegis.core.plugin.ReportRenderer;
import com.aegis.core.report.MissionReportData;

/**
 * Worked example of a Stage 2 "Report Plugin": a minimal Markdown
 * summary alongside AEGIS's 3 built-in formats. Registered via
 * {@code META-INF/services/com.aegis.core.plugin.ReportRenderer}.
 */
public class MarkdownReportRenderer implements ReportRenderer {

    @Override
    public String name() {
        return "markdown";
    }

    @Override
    public String render(MissionReportData data) {

        StringBuilder markdown = new StringBuilder();

        markdown.append("# AEGIS Mission Report (plugin-example)\n\n");
        markdown.append("- **Mission:** ").append(data.missionName()).append('\n');
        markdown.append("- **Goal:** ").append(data.missionGoal()).append('\n');
        markdown.append("- **Result:** ").append(data.status()).append('\n');
        markdown.append("- **Duration:** ").append(data.duration()).append('\n');
        markdown.append("- **Actions executed:** ").append(data.actionsExecuted()).append('\n');
        markdown.append("- **Average confidence:** ")
                .append(String.format("%.2f", data.averageConfidence())).append('\n');
        markdown.append("- **Coverage:** ")
                .append(String.format("%.0f%%", data.coverage().coveragePercent()))
                .append(" (").append(data.coverage().elementsInteracted())
                .append("/").append(data.coverage().elementsDiscovered()).append(" elements)\n");
        markdown.append("- **Findings:** ").append(data.findings().size()).append('\n');

        if (!data.findings().isEmpty()) {
            markdown.append("\n## Findings\n\n");
            for (var finding : data.findings()) {
                markdown.append("- `").append(finding.severity()).append("` ")
                        .append(finding.summary()).append('\n');
            }
        }

        return markdown.toString();
    }
}
