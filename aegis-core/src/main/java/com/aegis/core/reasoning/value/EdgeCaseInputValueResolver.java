package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;

import java.util.List;

/**
 * Deliberately returns malformed/edge-case values instead of realistic
 * ones, to probe whether a field's client-side validation and the app's
 * error handling actually hold under bad input: empty submissions,
 * oversized strings, XSS/SQL-injection-shaped payloads, malformed
 * emails, unicode, and whitespace-only values.
 *
 * Selected per mission via the "inputStrategy" mission parameter (value
 * "edge-case") — see InputValueResolverRegistry — alongside the default,
 * realistic DefaultInputValueResolver. Deliberately makes no attempt to
 * still let the mission succeed: the point of running with this resolver
 * is to see what breaks, not to reach the goal.
 *
 * Value choice is deterministic per field, derived from its id/name
 * rather than call order or randomness, so the same field gets the same
 * edge case on identical runs, and a field whose TYPE candidate gets
 * regenerated across several iterations before it's actually executed
 * doesn't churn through different values along the way.
 */
public class EdgeCaseInputValueResolver implements InputValueResolver {

    private static final List<String> GENERIC_EDGE_CASES = List.of(
            "",
            " ",
            "a".repeat(5000),
            "<script>alert('aegis')</script>",
            "' OR '1'='1'; DROP TABLE users; --",
            "🔥💥🧪 Ω≈ç√∫˜µ≤≥÷",
            "  leading and trailing whitespace  ",
            "null",
            "-1",
            "../../../../etc/passwd"
    );

    private static final List<String> EMAIL_EDGE_CASES = List.of(
            "",
            "not-an-email",
            "missing-at-sign.com",
            "@missing-local-part.com",
            "trailing-dot@example.com.",
            "a".repeat(250) + "@example.com",
            "<script>alert(1)</script>@example.com",
            "unicode.tester.日本語@example.com"
    );

    private static final List<String> PASSWORD_EDGE_CASES = List.of(
            "",
            "a",
            "        ",
            "123456",
            "a".repeat(5000),
            "<script>alert('aegis')</script>",
            "pässwörd-ünïcödé"
    );

    @Override
    public String resolve(MissionContext context, ElementInfo element) {

        FieldPurpose.Purpose purpose = FieldPurpose.of(element);

        List<String> bank = switch (purpose) {
            case EMAIL -> EMAIL_EDGE_CASES;
            case PASSWORD -> PASSWORD_EDGE_CASES;
            case USERNAME, GENERIC -> GENERIC_EDGE_CASES;
        };

        return bank.get(indexFor(element, bank.size()));
    }

    private int indexFor(ElementInfo element, int bankSize) {

        String identity = safe(element.id()) + "|" + safe(element.name());

        return Math.floorMod(identity.hashCode(), bankSize);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
