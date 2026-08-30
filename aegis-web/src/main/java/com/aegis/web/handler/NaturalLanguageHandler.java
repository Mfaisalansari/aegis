package com.aegis.web.handler;

import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.mission.LlmMissionParser;
import com.aegis.core.mission.LlmMissionParser.NaturalLanguageParseResult;
import com.aegis.web.form.FormBodyParser;
import com.aegis.web.form.MissionFormRequest;
import com.aegis.web.view.MissionFormView;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@code POST /run/parse} — turns a plain-English instruction into a pre-filled, reviewable
 * mission form. Never runs anything itself; that only happens if the user then submits the
 * (editable) structured form via the normal {@code POST /run} path. {@link LlmMissionParser}'s
 * {@link LlmMissionParser#parseWithDiagnostics} never throws and never returns a null mission
 * (it falls back to {@code RuleBasedMissionParser} internally on any failure) — this handler
 * needs no try/catch of its own — but unlike the plain {@code parse(String)} method, it also
 * says whether that fallback happened, so the rendered notice can say so instead of presenting
 * a mostly-empty, silently-degraded result exactly like a full success.
 *
 * Constructor-injectable for tests (mirrors {@code RunHandler}/{@code DashboardHandler}); the
 * real {@code render(Map)} logic takes no {@link HttpExchange} at all, the same reason
 * {@code MissionFormView.render(...)} is tested by calling it directly rather than through HTTP.
 */
public final class NaturalLanguageHandler implements HttpHandler {

    private final LlmMissionParser parser;

    public NaturalLanguageHandler() {
        this(new LlmMissionParser(OpenAiCompatibleChatClient.fromEnvironment()));
    }

    NaturalLanguageHandler(LlmMissionParser parser) {
        this.parser = parser;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> values = FormBodyParser.parse(HandlerSupport.readBody(exchange));
        HandlerSupport.sendHtml(exchange, 200, render(values));
    }

    String render(Map<String, String> values) {

        String instruction = values.getOrDefault("instruction", "");

        if (instruction.isBlank()) {
            return MissionFormView.render(MissionFormRequest.defaults(),
                    List.of("Please describe the mission before parsing."));
        }

        NaturalLanguageParseResult result = parser.parseWithDiagnostics(instruction);
        MissionFormRequest form = MissionFormRequest.fromParsedMission(result.mission(), instruction);

        String notice = result.aiUsed()
                ? "Parsed from your description — review the fields below, then click Start run."
                : "AI parsing wasn't available (" + result.fallbackReason() + ") — only a URL was extracted "
                        + "using the plain-text fallback. Please review and fill in the rest below.";

        return MissionFormView.render(form, List.of(), notice);
    }
}
