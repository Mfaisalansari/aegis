package com.aegis.core.reasoning.value;

import com.aegis.model.context.MissionContext;
import com.aegis.model.mission.Mission;
import com.aegis.model.observation.ElementInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultInputValueResolverTest {

    private final DefaultInputValueResolver resolver = new DefaultInputValueResolver();

    @Test
    void usesMissionUsernameForUsernameField() {

        MissionContext context = context(Map.of("username", "tomsmith"));

        assertEquals("tomsmith", resolver.resolve(context, input("username", "username", "text")));
    }

    @Test
    void usesMissionPasswordForPasswordField() {

        MissionContext context = context(Map.of("password", "s3cret!"));

        assertEquals("s3cret!", resolver.resolve(context, input("Password", "Password", "password")));
    }

    @Test
    void fallsBackToDefaultUsernameWhenMissionHasNone() {

        MissionContext context = context(Map.of());

        assertEquals("aegis.tester", resolver.resolve(context, input("username", "username", "text")));
    }

    @Test
    void fallsBackToDefaultPasswordWhenMissionHasNone() {

        MissionContext context = context(Map.of());

        assertEquals("Aegis!2345", resolver.resolve(context, input("Password", "Password", "password")));
    }

    @Test
    void emailFieldUsesMissionUsernameWhenItLooksLikeAnEmail() {

        MissionContext context = context(Map.of("username", "tester@example.com"));

        assertEquals("tester@example.com", resolver.resolve(context, input("Email", "Email", "text")));
    }

    @Test
    void emailFieldGeneratesAValidLookingEmailWhenMissionUsernameIsNotOne() {

        MissionContext context = context(Map.of("username", "tomsmith"));

        String value = resolver.resolve(context, input("Email", "Email", "text"));

        assertTrue(value.contains("@"), "generated email fallback should contain @: " + value);
    }

    @Test
    void emailFieldGeneratesAValidLookingEmailWhenMissionHasNoUsernameAtAll() {

        MissionContext context = context(Map.of());

        String value = resolver.resolve(context, input("Email", "Email", "text"));

        assertTrue(value.contains("@"), "generated email fallback should contain @: " + value);
    }

    @Test
    void genericFieldGetsThePlaceholderValue() {

        MissionContext context = context(Map.of());

        assertEquals("sample", resolver.resolve(context, input("FirstName", "FirstName", "text")));
    }

    private MissionContext context(Map<String, String> parameters) {
        return new MissionContext(new Mission(UUID.randomUUID(), "Test", "Test", parameters));
    }

    private ElementInfo input(String name, String id, String type) {
        return new ElementInfo("input", id, name, "", type, "", true, true, "#" + id);
    }
}
