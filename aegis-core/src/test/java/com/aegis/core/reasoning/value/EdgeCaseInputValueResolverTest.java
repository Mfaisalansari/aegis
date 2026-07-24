package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeCaseInputValueResolverTest {

    // Mirrors the resolver's own banks, so tests verify real bank content
    // (and that FieldPurpose routing picks the right bank) rather than
    // asserting a tautology.
    private static final List<String> EMAIL_EDGE_CASES = List.of(
            "", "not-an-email", "missing-at-sign.com", "@missing-local-part.com",
            "trailing-dot@example.com.", "a".repeat(250) + "@example.com",
            "<script>alert(1)</script>@example.com", "unicode.tester.日本語@example.com"
    );

    private static final List<String> PASSWORD_EDGE_CASES = List.of(
            "", "a", "        ", "123456", "a".repeat(5000),
            "<script>alert('aegis')</script>", "pässwörd-ünïcödé"
    );

    private final EdgeCaseInputValueResolver resolver = new EdgeCaseInputValueResolver();

    @Test
    void ignoresMissionCredentialsEntirely() {

        MissionContext context = context(Map.of("username", "tomsmith", "password", "s3cret!"));

        String value = resolver.resolve(context, input("username", "username", "text"));

        assertNotEquals("tomsmith", value, "edge-case resolver must not fall back to realistic values");
    }

    @Test
    void emailFieldGetsAMalformedEmailShapedValueFromTheEmailBank() {

        MissionContext context = context(Map.of());

        String value = resolver.resolve(context, input("Email", "Email", "email"));

        assertTrue(EMAIL_EDGE_CASES.contains(value), "expected a value from the email edge-case bank, got: " + value);
    }

    @Test
    void passwordFieldGetsAValueFromThePasswordBankNotARealisticPassword() {

        MissionContext context = context(Map.of());

        String value = resolver.resolve(context, input("Password", "Password", "password"));

        assertTrue(PASSWORD_EDGE_CASES.contains(value), "expected a value from the password edge-case bank, got: " + value);
        assertNotEquals("Aegis!2345", value);
    }

    @Test
    void sameFieldGetsTheSameEdgeCaseOnRepeatedCalls() {

        MissionContext context = context(Map.of());
        ElementInfo field = input("Email", "Email", "email");

        String first = resolver.resolve(context, field);
        String second = resolver.resolve(context, field);

        assertEquals(first, second, "must be deterministic per field, not random or call-order dependent");
    }

    @Test
    void genericFieldValueDoesNotComeFromTheRealisticPlaceholder() {

        MissionContext context = context(Map.of());

        String value = resolver.resolve(context, input("FirstName", "FirstName", "text"));

        assertNotEquals("sample", value, "DefaultInputValueResolver's placeholder must not leak through");
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }

    private ElementInfo input(String name, String id, String type) {
        return new ElementInfo("input", id, name, "", type, "", true, true, "#" + id);
    }
}
