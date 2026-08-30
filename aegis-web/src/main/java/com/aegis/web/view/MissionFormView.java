package com.aegis.web.view;

import com.aegis.web.form.MissionFormRequest;

import java.util.List;

import static com.aegis.web.view.Layout.escapeHtml;

/** Renders {@code GET /} (empty form) and the sticky re-render after an invalid {@code POST /run}. */
public final class MissionFormView {

    private MissionFormView() {
    }

    public static String render(MissionFormRequest form, List<String> errors) {
        return render(form, errors, null);
    }

    public static String render(MissionFormRequest form, List<String> errors, String notice) {

        StringBuilder body = new StringBuilder();

        body.append("<h1>Run a Mission</h1>");
        body.append("<p class=\"lede\">Point it at a target and a success condition &mdash; AEGIS observes the live page and decides each next action on its own.</p>");

        if (!errors.isEmpty()) {
            body.append("<div class=\"errors\"><strong>Please fix the following:</strong><ul>");
            for (String error : errors) {
                body.append("<li>").append(escapeHtml(error)).append("</li>");
            }
            body.append("</ul></div>");
        }

        if (notice != null) {
            body.append("<div class=\"notice\">").append(escapeHtml(notice)).append("</div>");
        }

        boolean hasInstruction = form.instruction() != null && !form.instruction().isBlank();

        body.append("<details class=\"card more-settings\"").append(hasInstruction ? " open" : "").append(">");
        body.append("<summary>Describe in plain English instead</summary><div class=\"more-body\">");
        body.append("<form method=\"post\" action=\"/run/parse\" id=\"nl-parse-form\">");
        body.append("<label for=\"instruction\">Mission Instruction</label>");
        body.append("<textarea id=\"instruction\" name=\"instruction\" placeholder=\"Log into https://example.com/ using username &quot;demo&quot; and password &quot;demo123&quot;, and confirm you reach the dashboard.\">")
                .append(escapeHtml(form.instruction())).append("</textarea>");
        body.append("<p class=\"help\">AEGIS extracts the starting URL, goal, success condition, and credentials, then pre-fills the form below for you to review before it runs anything.</p>");
        body.append("<button type=\"submit\" class=\"btn-secondary\" style=\"margin-top:10px\">Parse into form</button>");
        body.append("</form>");
        body.append("</div></details>");

        body.append("<form method=\"post\" action=\"/run\">");

        body.append("<section class=\"card\">");
        body.append(textField("baseUrl", "Base URL", form.baseUrl(), "https://example.com/", HelpText.BASE_URL));
        body.append(textField("successUrlContains", "Success When URL Contains", form.successUrlContains(), "e.g. inventory.html", HelpText.SUCCESS_URL_CONTAINS));
        body.append(selectField("strategy", "Exploration Strategy", form.strategy(), strategyKeys(), null));
        body.append(strategyHelp(form.strategy()));
        body.append("</section>");

        body.append("<details class=\"card more-settings\"><summary>More settings</summary><div class=\"more-body\">");

        body.append("<div class=\"subsection\"><h3>Login</h3>");
        body.append("<div class=\"grid-2\">");
        body.append(textField("username", "Username", form.username(), "", null));
        body.append(passwordField("password", "Password", form.password()));
        body.append("</div>");
        body.append("<p class=\"help\">").append(escapeHtml(HelpText.CREDENTIALS)).append("</p>");
        body.append("</div>");

        body.append("<div class=\"subsection\"><h3>Browser &amp; Run Limits</h3>");
        body.append("<div class=\"grid-3\">");
        body.append(selectField("browserType", "Engine", form.browserType(), List.of("chromium", "firefox", "webkit"), null));
        body.append(textField("maxIterations", "Max Iterations", form.maxIterations(), "10", null));
        body.append(selectField("inputStrategy", "Input Strategy", form.inputStrategy(), List.of("realistic", "edge-case"), null));
        body.append("</div>");
        body.append(toggleRow("headless", "Headless", form.headless(), HelpText.HEADLESS));
        body.append(toggleRow("interruptions", "Interruptions", form.interruptions(), HelpText.INTERRUPTIONS));
        body.append(toggleRow("doubleClicks", "Double-Clicks", form.doubleClicks(), HelpText.DOUBLE_CLICKS));
        body.append(toggleRow("raceConditions", "Race Conditions", form.raceConditions(), HelpText.RACE_CONDITIONS));
        body.append("</div>");

        body.append("<div class=\"subsection\"><h3>Inspection &amp; Report</h3>");
        body.append(toggleRow("captureDom", "Capture DOM", form.captureDom(), HelpText.CAPTURE_DOM));
        body.append(toggleRow("consoleWarnings", "Include Console Warnings", form.consoleWarnings(), HelpText.CONSOLE_WARNINGS));
        body.append(toggleRow("probeLinks", "Probe Links", form.probeLinks(), HelpText.PROBE_LINKS));
        body.append("<div class=\"grid-2\">");
        body.append(textField("contrastThreshold", "Contrast Threshold", form.contrastThreshold(), "4.5", null));
        body.append(textField("reportDirectory", "Report Directory", form.reportDirectory(), "reports", null));
        body.append("</div>");
        body.append(textAreaField("noiseDenyPatterns", "Noise Deny Patterns", form.noiseDenyPatterns(), HelpText.NOISE_DENY_PATTERNS));
        body.append(textAreaField("knowledgeYaml", "Knowledge YAML", form.knowledgeYaml(), HelpText.KNOWLEDGE_YAML));
        body.append("</div>");

        body.append("<div class=\"subsection\"><h3>LLM Settings (read-only)</h3>");
        body.append("<p class=\"help\">").append(escapeHtml(HelpText.LLM_NOTE)).append("</p>");
        body.append("<div class=\"llm-list\">");
        for (String var : HelpText.LLM_ENV_VARS) {
            boolean set = System.getenv(var) != null;
            body.append("<div class=\"llm-row\"><span>").append(escapeHtml(var)).append("</span><span class=\"")
                    .append(set ? "set" : "unset").append("\">").append(set ? "set" : "not set").append("</span></div>");
        }
        body.append("</div></div>");

        body.append("</div></details>");

        body.append("<button type=\"submit\">Start run</button>");
        body.append("</form>");

        body.append(PARSING_OVERLAY);
        body.append(strategyHelpScript());

        return Layout.page("AEGIS — Run a Mission", "run", body.toString());
    }

    /**
     * Parsing is a real (non-AJAX) POST to /run/parse that blocks on an LLM call, so there is no
     * client-side progress to report — this shows an indeterminate bar and blocks input until the
     * server responds with the pre-filled form. The disable pass runs on the next tick (setTimeout 0)
     * so it happens after the browser has already read the submitting form's field values.
     */
    private static final String PARSING_OVERLAY = """
            <div class="parsing-overlay" id="parsing-overlay" role="status" aria-live="polite">
              <div class="parsing-box">
                <div class="progress-track"><div class="progress-fill"></div></div>
                <p>Parsing your mission with AI&hellip;</p>
              </div>
            </div>
            <script>
            (function () {
                var nlForm = document.getElementById('nl-parse-form');
                if (!nlForm) { return; }
                nlForm.addEventListener('submit', function () {
                    document.body.classList.add('is-parsing');
                    document.getElementById('parsing-overlay').classList.add('active');
                    setTimeout(function () {
                        document.querySelectorAll('input, select, textarea, button').forEach(function (el) {
                            el.setAttribute('disabled', 'disabled');
                        });
                    }, 0);
                });
            })();
            </script>
            """;

    private static List<String> strategyKeys() {
        return List.of("greedy", "random", "risk-based", "breadth-first", "depth-first",
                "form-first", "navigation-first", "coverage-aware", "adaptive", "llm", "knowledge-aware");
    }

    private static String strategyHelp(String selected) {

        String chosen = selected == null || selected.isBlank() ? "greedy" : selected;

        for (String[] entry : HelpText.STRATEGIES) {
            if (entry[0].equals(chosen)) {
                return "<p class=\"help\" id=\"strategy-help\"><code>" + escapeHtml(entry[0]) + "</code> &mdash; " + escapeHtml(entry[1]) + "</p>";
            }
        }

        return "<p class=\"help\" id=\"strategy-help\"></p>";
    }

    /**
     * The server only renders the description matching whatever strategy
     * was selected on the last GET/POST — with no client-side wiring at
     * all, picking a different option in the dropdown left the help text
     * frozen on the old selection until a full page reload. This updates
     * it live: the same {@link HelpText#STRATEGIES} data already used for
     * the server-rendered initial text, re-embedded here as a small JS
     * lookup object so no new API endpoint is needed — same "everything
     * inline" convention {@link #PARSING_OVERLAY} already established in
     * this file.
     */
    private static String strategyHelpScript() {

        StringBuilder descriptions = new StringBuilder("{");

        for (int i = 0; i < HelpText.STRATEGIES.length; i++) {
            String[] entry = HelpText.STRATEGIES[i];
            if (i > 0) {
                descriptions.append(",");
            }
            descriptions.append("\"").append(jsStringLiteral(entry[0])).append("\":\"").append(jsStringLiteral(entry[1])).append("\"");
        }

        descriptions.append("}");

        return "<script>\n"
                + "(function () {\n"
                + "    var descriptions = " + descriptions + ";\n"
                + "    var select = document.getElementById('strategy');\n"
                + "    var help = document.getElementById('strategy-help');\n"
                + "    if (!select || !help) { return; }\n"
                + "    select.addEventListener('change', function () {\n"
                + "        var text = descriptions[select.value];\n"
                + "        help.innerHTML = text ? '<code>' + select.value + '</code> &mdash; ' + text : '';\n"
                + "    });\n"
                + "})();\n"
                + "</script>\n";
    }

    /** HTML-escapes first (this ends up in innerHTML), then JS-escapes so the result is a syntactically valid single JS string literal. */
    private static String jsStringLiteral(String value) {

        String htmlEscaped = escapeHtml(value);
        StringBuilder out = new StringBuilder(htmlEscaped.length());

        for (int i = 0; i < htmlEscaped.length(); i++) {
            char c = htmlEscaped.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }

        return out.toString();
    }

    private static String textField(String name, String label, String value, String placeholder, String help) {

        StringBuilder field = new StringBuilder();
        field.append("<div><label for=\"").append(name).append("\">").append(escapeHtml(label)).append("</label>");
        field.append("<input type=\"text\" id=\"").append(name).append("\" name=\"").append(name)
                .append("\" value=\"").append(escapeHtml(value)).append("\" placeholder=\"").append(escapeHtml(placeholder)).append("\">");
        if (help != null) {
            field.append("<p class=\"help\">").append(escapeHtml(help)).append("</p>");
        }
        field.append("</div>");

        return field.toString();
    }

    private static String passwordField(String name, String label, String value) {
        return "<div><label for=\"" + name + "\">" + escapeHtml(label) + "</label>"
                + "<input type=\"password\" id=\"" + name + "\" name=\"" + name + "\" value=\"" + escapeHtml(value) + "\"></div>";
    }

    private static String textAreaField(String name, String label, String value, String help) {

        StringBuilder field = new StringBuilder();
        field.append("<label for=\"").append(name).append("\">").append(escapeHtml(label)).append("</label>");
        field.append("<textarea id=\"").append(name).append("\" name=\"").append(name).append("\">")
                .append(escapeHtml(value)).append("</textarea>");
        if (help != null) {
            field.append("<p class=\"help\">").append(escapeHtml(help)).append("</p>");
        }

        return field.toString();
    }

    private static String selectField(String name, String label, String selected, List<String> options, String help) {

        StringBuilder field = new StringBuilder();
        field.append("<div><label for=\"").append(name).append("\">").append(escapeHtml(label)).append("</label>");
        field.append("<select id=\"").append(name).append("\" name=\"").append(name).append("\">");

        boolean matchesKnownOption = false;

        for (String option : options) {
            boolean isSelected = option.equalsIgnoreCase(selected);
            matchesKnownOption |= isSelected;
            field.append("<option value=\"").append(escapeHtml(option)).append("\"").append(isSelected ? " selected" : "").append(">")
                    .append(escapeHtml(option)).append("</option>");
        }

        if (selected != null && !selected.isBlank() && !matchesKnownOption) {
            field.append("<option value=\"").append(escapeHtml(selected)).append("\" selected>")
                    .append(escapeHtml(selected)).append("</option>");
        }

        field.append("</select>");
        if (help != null) {
            field.append("<p class=\"help\">").append(escapeHtml(help)).append("</p>");
        }
        field.append("</div>");

        return field.toString();
    }

    private static String toggleRow(String name, String label, boolean checked, String hint) {
        return "<label class=\"toggle-row\" for=\"" + name + "\">"
                + "<input type=\"checkbox\" id=\"" + name + "\" name=\"" + name + "\"" + (checked ? " checked" : "") + ">"
                + "<span class=\"toggle-label\">" + escapeHtml(label) + "</span>"
                + (hint != null ? "<span class=\"toggle-hint\">&mdash; " + escapeHtml(hint) + "</span>" : "")
                + "</label>";
    }
}
