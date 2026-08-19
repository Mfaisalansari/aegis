package com.aegis.core.report;

import com.aegis.core.knowledge.InspectionCheckType;
import com.aegis.core.knowledge.UxFindingType;
import com.aegis.model.action.ActionType;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.mission.MissionStatus;

/**
 * Fixed, deterministic term translations for everything in the HTML report
 * that would otherwise leak a raw enum name or internal-engineering word
 * to a reader who isn't an engineer — severity, finding categories/types,
 * action verbs, mission status. Deliberately rule-based, not LLM-backed:
 * there's no reason to add model latency/dependency for translating a
 * fixed 4-, 6-, 8-, or 11-value enum, same "always usable without AI"
 * discipline as {@code RuleBasedBugExplainer}.
 *
 * Every method is a {@code switch} expression with no {@code default} arm
 * — adding a new enum constant without a translation is a compile error
 * here, not a silent gap a reader discovers as a bare enum name.
 */
final class PlainLanguageGlossary {

    private PlainLanguageGlossary() {
    }

    static String severityLabel(FindingSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "Urgent";
            case HIGH -> "Important";
            case MEDIUM -> "Worth Fixing";
            case LOW -> "Minor";
        };
    }

    /** One clause explaining why this severity level matters — used as a tooltip alongside the badge. */
    static String severityWhyItMatters(FindingSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "blocks users from completing what they came to do";
            case HIGH -> "significantly hurts the experience";
            case MEDIUM -> "a real issue, but not blocking";
            case LOW -> "a small polish item";
        };
    }

    static String categoryLabel(FindingCategory category) {
        return switch (category) {
            case JAVASCRIPT -> "Technical Errors";
            case NETWORK -> "Loading Problems";
            case NAVIGATION -> "Navigation Issues";
            case TIMEOUT -> "Slow Responses";
            case STABILITY -> "Crashes";
            case OTHER -> "Other Issues";
        };
    }

    static String uxFindingLabel(UxFindingType type) {
        return switch (type) {
            case BACKTRACKING -> "Repeated Back-and-Forth Navigation";
            case JOURNEY_DIVERGENCE -> "Off the Expected Path";
            case NAVIGATION_FRICTION -> "Took More Steps Than Expected";
            case MISSING_ACCESSIBLE_NAME -> "Unlabeled Interactive Element";
        };
    }

    static String uxFindingExplanation(UxFindingType type) {
        return switch (type) {
            case BACKTRACKING -> "A user had to revisit the same screen multiple times to get where they were going.";
            case JOURNEY_DIVERGENCE -> "The path a user actually took didn't match the expected route through the site.";
            case NAVIGATION_FRICTION -> "Completing this task took more screens than it should have.";
            case MISSING_ACCESSIBLE_NAME ->
                    "A button or link has no text a screen reader can announce, so assistive-technology users can't tell what it does.";
        };
    }

    static String inspectionCheckLabel(InspectionCheckType type) {
        return switch (type) {
            case CONSOLE_ERROR -> "Console Error";
            case UNCAUGHT_EXCEPTION -> "Unhandled Error";
            case NETWORK_FAILURE -> "Failed Network Request";
            case BROKEN_LINK -> "Broken Link";
            case LOW_CONTRAST -> "Hard-to-Read Text";
            case MISSING_ACCESSIBLE_NAME -> "Unlabeled Interactive Element";
            case ZERO_SIZE_ELEMENT -> "Invisible Element";
            case TEXT_OVERFLOW -> "Cut-Off Text";
        };
    }

    static String inspectionCheckExplanation(InspectionCheckType type) {
        return switch (type) {
            case CONSOLE_ERROR -> "The browser logged a technical error while the page was running.";
            case UNCAUGHT_EXCEPTION -> "The page's code hit an error it didn't handle — something likely didn't work as intended.";
            case NETWORK_FAILURE -> "Something the page needed failed to load.";
            case BROKEN_LINK -> "A link on the page points somewhere that doesn't work.";
            case LOW_CONTRAST -> "Text color is too close to its background color for many users to read comfortably.";
            case MISSING_ACCESSIBLE_NAME ->
                    "A button or link has no text a screen reader can announce, so assistive-technology users can't tell what it does.";
            case ZERO_SIZE_ELEMENT -> "An element takes up no visible space, so a real user could never see or use it.";
            case TEXT_OVERFLOW -> "Text is clipped by its container and part of it isn't visible.";
        };
    }

    /** A past-tense verb phrase for use in "AEGIS ___ X" — e.g. "AEGIS clicked the login button". */
    static String actionVerb(ActionType type) {
        return switch (type) {
            case CLICK -> "clicked";
            case DOUBLE_CLICK -> "double-clicked";
            case RACE_CLICK -> "clicked (whichever appeared first)";
            case TYPE -> "typed into";
            case SELECT -> "selected an option in";
            case WAIT -> "waited on";
            case SCROLL -> "scrolled to";
            case NAVIGATE -> "went to";
            case REFRESH -> "refreshed";
            case BACK -> "went back from";
            case COMPLETE -> "finished";
        };
    }

    static String missionStatusLabel(MissionStatus status) {
        return switch (status) {
            case SUCCESS -> "Succeeded";
            case FAILED -> "Failed";
            case PARTIAL -> "Partially Completed";
        };
    }
}
