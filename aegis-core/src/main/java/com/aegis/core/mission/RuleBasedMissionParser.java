package com.aegis.core.mission;

import com.aegis.model.mission.Mission;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default, always-available MissionParser: no model call, just a URL
 * regex against the raw text — the one thing a natural-language QA
 * instruction almost always contains and that's mechanically extractable
 * without understanding the sentence around it. Everything else
 * (success condition, credentials) is left unset; there's no honest
 * rule-based way to infer those from arbitrary prose. Used unless AI
 * mission parsing is explicitly opted into (see LlmMissionParser).
 */
public class RuleBasedMissionParser implements MissionParser {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?)\\]]+$");

    @Override
    public Mission parse(String naturalLanguageDescription) {

        String text = naturalLanguageDescription == null ? "" : naturalLanguageDescription;
        String baseUrl = extractUrl(text);

        return new Mission(
                UUID.randomUUID(),
                shortName(text),
                text,
                baseUrl == null ? Map.of() : Map.of("baseUrl", baseUrl)
        );
    }

    private String extractUrl(String text) {

        Matcher matcher = URL_PATTERN.matcher(text);

        if (!matcher.find()) {
            return null;
        }

        return TRAILING_PUNCTUATION.matcher(matcher.group()).replaceAll("");
    }

    private String shortName(String text) {

        String trimmed = text.strip();

        return trimmed.length() > 60 ? trimmed.substring(0, 57) + "..." : trimmed;
    }
}
