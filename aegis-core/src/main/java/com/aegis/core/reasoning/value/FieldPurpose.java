package com.aegis.core.reasoning.value;

import com.aegis.model.observation.ElementInfo;

/**
 * Classifies what an input field is likely for, based on its name/id/type,
 * so confidence estimation and value resolution agree on what counts as a
 * "credential-like" field instead of drifting independently.
 *
 * Deliberately narrow: username/email/password only. Sites that identify
 * their login field some other way (e.g. a phone number) won't be
 * recognized — extending the pattern list is a follow-up, not a promise
 * this covers every site.
 */
public final class FieldPurpose {

    private FieldPurpose() {
    }

    public enum Purpose {
        USERNAME, EMAIL, PASSWORD, GENERIC
    }

    public static Purpose of(ElementInfo element) {

        String identifier = (safe(element.name()) + " " + safe(element.id())).toLowerCase();
        String type = safe(element.type()).toLowerCase();

        if (identifier.contains("pass")) {
            return Purpose.PASSWORD;
        }

        if (identifier.contains("email") || type.equals("email")) {
            return Purpose.EMAIL;
        }

        if (identifier.contains("user")) {
            return Purpose.USERNAME;
        }

        return Purpose.GENERIC;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
