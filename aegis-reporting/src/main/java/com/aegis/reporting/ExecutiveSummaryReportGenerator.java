package com.aegis.reporting;

import java.util.List;

/**
 * A single-page, plain-language HTML summary of a {@link RunSummary} (or
 * a whole suite's worth) — for a reader who wants "is it OK, and what
 * did we get for running this" in ten seconds, not a table of findings.
 * Self-contained (inline CSS, no external assets) — same portability
 * convention as the frozen {@code HtmlExplainabilityReportGenerator} this
 * sits alongside, not inside.
 *
 * The "technical detail" link assumes a caller names its own
 * scenario-level technical report the same way {@code Hooks.java}'s own
 * convention already does: the scenario name, non-alphanumerics replaced
 * with {@code -}, plus {@code .html} — e.g. {@code "Successful Payment"}
 * → {@code "Successful-Payment.html"}. If a caller's naming differs, the
 * link simply won't resolve; this generator has no way to know a
 * caller's actual file layout.
 */
public final class ExecutiveSummaryReportGenerator {

    public String generate(String applicationName, RunSummary summary) {

        String scoreBand = scoreBandOf(summary.experienceScore());
        String statusLabel = summary.passed() ? "Passed" : "Failed";
        String statusClass = summary.passed() ? "pass" : "fail";

        StringBuilder body = new StringBuilder();

        body.append(header(applicationName, summary.name()));
        body.append("<div class='score-row'>");
        body.append("<div class='score ").append(scoreBand).append("'>").append(summary.experienceScore()).append("<span>/100</span></div>");
        body.append("<div class='status-badge ").append(statusClass).append("'>").append(statusLabel).append("</div>");
        body.append("</div>");

        body.append("<p class='narrative'>").append(escape(applicationName)).append(' ')
                .append(ReportLanguage.narrativeFor(summary)).append(".</p>");

        body.append(roiSection(summary.healCount(), summary.selfInputCount()));
        body.append(learningSection(summary.newlyDiscoveredCount(), summary.untestedCount(), summary.learningNotes()));
        body.append(findingsSection(summary.notableFindings(), summary.criticalFindingCount()));

        String link = slug(summary.name()) + ".html";
        body.append("<p class='detail-link'><a href='").append(escape(link)).append("'>View technical detail →</a></p>");

        return page(applicationName + " — " + summary.name(), body.toString());
    }

    /** Same suite report, without a coverage-learning section — for a caller that never wired up cross-run coverage tracking at all. */
    public String generate(String applicationName, List<RunSummary> summaries) {
        return generate(applicationName, summaries, 0, 0, List.of());
    }

    /**
     * Suite pass/fail, score, and ROI aggregate cleanly by summing each
     * scenario's own {@link RunSummary} (each scenario's heal count is
     * independent of every other scenario's). Coverage learning does NOT
     * aggregate that way — a per-scenario "untested" count would compare
     * one scenario's own narrow slice of pages against the WHOLE app's
     * history, flagging every other scenario's pages as "missing" on
     * every single run, which is exactly backwards. So this overload
     * takes the suite-wide coverage numbers as explicit arguments,
     * computed once by the caller from the union of every scenario's
     * pages against the app's history before this run — see {@link
     * #generate(String, List)} for the version without them.
     */
    public String generate(String applicationName, List<RunSummary> summaries,
                            int suiteNewlyDiscoveredCount, int suiteUntestedCount, List<String> suiteLearningNotes) {

        long passedCount = summaries.stream().filter(RunSummary::passed).count();
        boolean allPassed = passedCount == summaries.size();
        int averageScore = summaries.isEmpty() ? 0
                : (int) Math.round(summaries.stream().mapToInt(RunSummary::experienceScore).average().orElse(0));

        int totalHeals = summaries.stream().mapToInt(RunSummary::healCount).sum();
        int totalSelfInputs = summaries.stream().mapToInt(RunSummary::selfInputCount).sum();

        List<RunSummary> needsAttention = summaries.stream()
                .filter(s -> !s.passed() || s.criticalFindingCount() > 0 || s.experienceScore() < 50)
                .toList();

        StringBuilder body = new StringBuilder();

        body.append(header(applicationName, "Full Regression Suite"));
        body.append("<div class='score-row'>");
        body.append("<div class='score ").append(scoreBandOf(averageScore)).append("'>").append(averageScore).append("<span>/100 (average)</span></div>");
        body.append("<div class='status-badge ").append(allPassed ? "pass" : "fail").append("'>")
                .append(passedCount).append(" / ").append(summaries.size()).append(" scenarios passed</div>");
        body.append("</div>");

        body.append(roiSection(totalHeals, totalSelfInputs));
        body.append(learningSection(suiteNewlyDiscoveredCount, suiteUntestedCount, suiteLearningNotes));

        body.append("<h2>Scenarios needing attention</h2>");
        if (needsAttention.isEmpty()) {
            body.append("<p class='clean'>None — every scenario is at or above the review threshold.</p>");
            if (suiteUntestedCount > 0) {
                body.append("<p class='attention-scope-note'>This checks pass/fail and score only — "
                        + "see “What AEGIS remembers” above for coverage changes, which is a separate question.</p>");
            }
        } else {
            body.append("<table class='attention-table'><thead><tr><th>Scenario</th><th>Status</th><th>Score</th></tr></thead><tbody>");
            for (RunSummary summary : needsAttention) {
                body.append("<tr><td><a href='").append(escape(slug(summary.name()))).append(".html'>")
                        .append(escape(summary.name())).append("</a></td><td>")
                        .append(summary.passed() ? "Passed" : "Failed").append("</td><td>")
                        .append(summary.experienceScore()).append("/100</td></tr>");
            }
            body.append("</tbody></table>");
        }

        body.append("<h2>All scenarios</h2>");
        body.append("<table class='attention-table'><thead><tr><th>Scenario</th><th>Status</th><th>Score</th><th></th></tr></thead><tbody>");
        for (RunSummary summary : summaries) {
            body.append("<tr><td>").append(escape(summary.name())).append("</td><td>")
                    .append(summary.passed() ? "Passed" : "Failed").append("</td><td>")
                    .append(summary.experienceScore()).append("/100</td><td>")
                    .append("<a href='").append(escape(slug(summary.name()))).append(".html'>View detail →</a></td></tr>");
        }
        body.append("</tbody></table>");

        return page(applicationName + " — Full Regression Suite", body.toString());
    }

    private String header(String applicationName, String runName) {
        return "<div class='header'><div class='app-name'>" + escape(applicationName) + "</div>"
                + "<div class='run-name'>" + escape(runName) + "</div></div>";
    }

    private String roiSection(int healCount, int selfInputCount) {

        if (healCount == 0 && selfInputCount == 0) {
            return "";
        }

        StringBuilder out = new StringBuilder("<div class='roi'><h2>What AEGIS did for you this run</h2><ul>");

        if (healCount > 0) {
            out.append("<li>🔧 <strong>").append(healCount).append(' ').append(ReportLanguage.pluralize(healCount, "locator"))
                    .append("</strong> auto-healed — approx. <strong>~").append(healCount * ReportLanguage.MINUTES_SAVED_PER_HEAL)
                    .append(" minutes</strong> of manual fixing avoided <span class='estimate'>(estimated)</span></li>");
        }

        if (selfInputCount > 0) {
            out.append("<li>✍️ <strong>").append(selfInputCount).append(' ').append(ReportLanguage.pluralize(selfInputCount, "field value"))
                    .append("</strong> auto-generated — approx. <strong>~").append(selfInputCount * ReportLanguage.MINUTES_SAVED_PER_SELF_INPUT)
                    .append(" minutes</strong> of test-data authoring avoided <span class='estimate'>(estimated)</span></li>");
        }

        out.append("</ul></div>");
        return out.toString();
    }

    private String learningSection(int newlyDiscoveredCount, int untestedCount, List<String> learningNotes) {

        if (newlyDiscoveredCount == 0 && untestedCount == 0 && learningNotes.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder("<div class='learning'><h2>What AEGIS remembers</h2>"
                + "<p class='learning-intro'>AEGIS keeps track of every page your tests have ever reached, "
                + "and compares this run against that history — this is where it flags what changed.</p><ul>");

        if (newlyDiscoveredCount > 0) {
            out.append("<li>🧭 <strong>").append(newlyDiscoveredCount).append(' ')
                    .append(ReportLanguage.pluralize(newlyDiscoveredCount, "screen")).append("</strong> seen for the first time this run</li>");
        }

        if (untestedCount > 0) {
            out.append("<li>⏮️ <strong>").append(untestedCount).append(' ')
                    .append(ReportLanguage.pluralize(untestedCount, "screen")).append("</strong> reached by a prior run but not this one</li>");
        }

        out.append("</ul>");

        if (!learningNotes.isEmpty()) {
            out.append("<p class='learning-notes'>");
            for (String note : learningNotes) {
                out.append(escape(note)).append("<br>");
            }
            out.append("</p>");
        }

        out.append("</div>");
        return out.toString();
    }

    private String findingsSection(List<String> notableFindings, int criticalFindingCount) {

        if (notableFindings.isEmpty()) {
            return "<div class='findings clean'><h2>Findings</h2><p>🎯 No issues worth flagging.</p></div>";
        }

        StringBuilder out = new StringBuilder("<div class='findings'><h2>Findings</h2>");
        out.append("<p class='finding-count'>").append(notableFindings.size()).append(' ')
                .append(ReportLanguage.pluralize(notableFindings.size(), "item")).append(" worth reviewing")
                .append(criticalFindingCount > 0 ? ", including at least one critical issue" : "").append(":</p><ul>");

        for (String finding : notableFindings) {
            out.append("<li>").append(escape(finding)).append("</li>");
        }

        out.append("</ul></div>");
        return out.toString();
    }

    private String scoreBandOf(int score) {
        if (score >= 80) {
            return "good";
        }
        if (score >= 50) {
            return "warn";
        }
        return "bad";
    }

    private String slug(String name) {
        return name.replaceAll("[^a-zA-Z0-9]+", "-");
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String page(String title, String body) {
        return "<!doctype html><html><head><meta charset='utf-8'><title>" + escape(title) + "</title>"
                + "<style>" + CSS + "</style></head><body><div class='card'>" + body + "</div></body></html>";
    }

    private static final String CSS = """
            @font-face { font-family:'Plex Sans'; src: local('IBM Plex Sans'), local('IBMPlexSans'); }
            @font-face { font-family:'Plex Mono'; src: local('IBM Plex Mono'), local('IBMPlexMono'); }
            :root {
                color-scheme: light dark;
                --ink:#14181f; --ink-soft:#3b414c; --muted:#6b7280; --faint:#9aa1ab;
                --paper:#ffffff; --surface:#f6f7f9; --surface-raised:#ffffff; --line:#e3e6ea;
                --accent:#3a6ea5; --accent-soft:#e9f0f8;
                --good:#1f8a5f; --good-soft:#e7f5ee;
                --warn:#b5760a; --warn-soft:#fbf1e2;
                --bad:#c1392b; --bad-soft:#fbeae8;
                --mono:'Plex Mono', ui-monospace, 'SFMono-Regular', Menlo, monospace;
                --sans:'Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            }
            @media (prefers-color-scheme: dark) {
                :root:not([data-theme="light"]) {
                    --ink:#eef0f3; --ink-soft:#c7ccd4; --muted:#9aa1ab; --faint:#6b7280;
                    --paper:#14161b; --surface:#1a1d23; --surface-raised:#20232a; --line:#2b2f37;
                    --accent:#7fb0e0; --accent-soft:#1c2b3a;
                    --good:#4fbf8f; --good-soft:#16281f;
                    --warn:#e0a748; --warn-soft:#2c2113;
                    --bad:#e8756a; --bad-soft:#2e1917;
                }
            }
            :root[data-theme="dark"] {
                --ink:#eef0f3; --ink-soft:#c7ccd4; --muted:#9aa1ab; --faint:#6b7280;
                --paper:#14161b; --surface:#1a1d23; --surface-raised:#20232a; --line:#2b2f37;
                --accent:#7fb0e0; --accent-soft:#1c2b3a;
                --good:#4fbf8f; --good-soft:#16281f;
                --warn:#e0a748; --warn-soft:#2c2113;
                --bad:#e8756a; --bad-soft:#2e1917;
            }
            * { box-sizing:border-box; }
            body { margin:0; padding:40px 20px; background:var(--surface); color:var(--ink);
                   font-family:var(--sans); }
            .card { max-width:640px; margin:0 auto; background:var(--surface-raised); border:1px solid var(--line);
                    border-radius:16px; padding:36px 40px; }
            .header .app-name { font-family:var(--mono); font-size:12px; font-weight:600; letter-spacing:0.06em;
                    text-transform:uppercase; color:var(--faint); }
            .header .run-name { font-size:22px; font-weight:650; margin-top:4px; letter-spacing:-0.01em; }
            .score-row { display:flex; align-items:center; gap:16px; margin:24px 0; }
            .score { font-family:var(--mono); font-size:52px; font-weight:700; line-height:1; padding:8px 20px; border-radius:12px; }
            .score span { font-size:16px; font-weight:600; opacity:0.7; }
            .score.good { color:var(--good); background:var(--good-soft); }
            .score.warn { color:var(--warn); background:var(--warn-soft); }
            .score.bad { color:var(--bad); background:var(--bad-soft); }
            .status-badge { font-family:var(--mono); font-size:12px; font-weight:700; letter-spacing:0.03em;
                    text-transform:uppercase; padding:7px 14px; border-radius:999px; }
            .status-badge.pass { color:var(--good); background:var(--good-soft); }
            .status-badge.fail { color:var(--bad); background:var(--bad-soft); }
            .narrative { font-size:16.5px; color:var(--ink-soft); line-height:1.6; }
            h2 { font-size:12.5px; font-weight:650; text-transform:uppercase; letter-spacing:0.04em;
                 color:var(--muted); margin:28px 0 10px; border-top:1px solid var(--line); padding-top:20px; }
            .roi ul, .findings ul, .learning ul { margin:0; padding-left:20px; line-height:1.7; }
            .estimate { color:var(--faint); font-weight:400; font-size:12.5px; }
            .learning-notes { color:var(--muted); font-size:12.5px; line-height:1.6; margin:10px 0 0; }
            .learning-intro { color:var(--muted); font-size:13.5px; line-height:1.5; margin:-4px 0 12px; }
            .findings.clean p, .clean { color:var(--good); }
            .attention-scope-note { color:var(--faint); font-size:12.5px; margin:6px 0 0; }
            .finding-count { color:var(--muted); margin:0 0 8px; }
            .attention-table { width:100%; border-collapse:collapse; font-size:13.5px; }
            .attention-table th { text-align:left; color:var(--muted); font-weight:600; font-size:11.5px;
                    text-transform:uppercase; letter-spacing:0.03em; padding:6px 8px; border-bottom:1px solid var(--line); }
            .attention-table td { padding:8px; border-bottom:1px solid var(--line); }
            .attention-table a { color:var(--accent); text-decoration:none; font-weight:600; }
            .detail-link { margin-top:24px; }
            .detail-link a { color:var(--accent); text-decoration:none; font-weight:600; font-size:13.5px; }
            """;
}
