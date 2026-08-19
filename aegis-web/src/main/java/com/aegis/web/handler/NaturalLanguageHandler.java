package com.aegis.web.handler;

import com.aegis.core.llm.OpenAiCompatibleChatClient;
import com.aegis.core.mission.LlmMissionParser;
import com.aegis.core.mission.MissionParser;
import com.aegis.model.mission.Mission;
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
 * (editable) structured form via the normal {@code POST /run} path. {@link MissionParser}'s
 * contract (never throws, never returns null — {@link LlmMissionParser} falls back to
 * {@code RuleBasedMissionParser} internally on any failure) means this handler needs no
 * try/catch of its own, the same trust the {@code NaturalLanguageMissionMain} example places
 * in it.
 */
public final class NaturalLanguageHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> values = FormBodyParser.parse(HandlerSupport.readBody(exchange));
        String instruction = values.getOrDefault("instruction", "");

        if (instruction.isBlank()) {
            String html = MissionFormView.render(MissionFormRequest.defaults(),
                    List.of("Please describe the mission before parsing."));
            HandlerSupport.sendHtml(exchange, 200, html);
            return;
        }

        MissionParser parser = new LlmMissionParser(OpenAiCompatibleChatClient.fromEnvironment());
        Mission mission = parser.parse(instruction);

        MissionFormRequest form = MissionFormRequest.fromParsedMission(mission, instruction);
        String notice = "Parsed from your description — review the fields below, then click Start run.";

        HandlerSupport.sendHtml(exchange, 200, MissionFormView.render(form, List.of(), notice));
    }
}
