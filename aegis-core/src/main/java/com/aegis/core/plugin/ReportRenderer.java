package com.aegis.core.plugin;

import com.aegis.core.report.MissionReportData;

/**
 * Stage 2 "Report Plugin": a custom output format alongside the 3
 * built-in ones (text/HTML/JSON) — a Markdown report, JUnit-XML for CI,
 * a Slack-message-shaped summary, whatever a consumer needs. Discovered
 * via {@link java.util.ServiceLoader}; every discovered renderer runs
 * once per mission against the same already-built {@link MissionReportData}
 * the built-in generators use, and its output is collected into
 * {@code AegisReport.pluginReports()} keyed by {@link #name()}.
 *
 * Unlike the 3 built-in generators (which independently rebuild
 * MissionReportData from raw context/experiences/screenshots via their
 * own overload chains, for historical reasons — see MissionReportData's
 * javadoc), a renderer here always receives the finished record, never
 * the raw pieces.
 */
public interface ReportRenderer {

    /** A short, filesystem-safe identifier — e.g. "markdown", "junit-xml". Used as the key in AegisReport.pluginReports(). */
    String name();

    String render(MissionReportData data);
}
