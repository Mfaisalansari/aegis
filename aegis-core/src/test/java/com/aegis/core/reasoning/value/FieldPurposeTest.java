package com.aegis.core.reasoning.value;

import com.aegis.model.observation.ElementInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldPurposeTest {

    @Test
    void recognizesUsernameByNameOrId() {
        assertEquals(FieldPurpose.Purpose.USERNAME, FieldPurpose.of(input("username", "username", "text")));
        assertEquals(FieldPurpose.Purpose.USERNAME, FieldPurpose.of(input("", "UserName", "text")));
    }

    @Test
    void recognizesEmailByNameOrType() {
        assertEquals(FieldPurpose.Purpose.EMAIL, FieldPurpose.of(input("Email", "Email", "text")));
        assertEquals(FieldPurpose.Purpose.EMAIL, FieldPurpose.of(input("contact", "contact", "email")));
        assertEquals(FieldPurpose.Purpose.EMAIL, FieldPurpose.of(input("NewsletterEmail", "newsletter-email", "text")));
    }

    @Test
    void recognizesPasswordByNameOrId() {
        assertEquals(FieldPurpose.Purpose.PASSWORD, FieldPurpose.of(input("Password", "Password", "password")));
        assertEquals(FieldPurpose.Purpose.PASSWORD, FieldPurpose.of(input("ConfirmPassword", "ConfirmPassword", "password")));
    }

    @Test
    void passwordTakesPriorityOverEmailWhenBothWouldMatch() {
        // a field literally named "passwordEmail" is unlikely, but the
        // priority should still be deterministic: password wins.
        assertEquals(FieldPurpose.Purpose.PASSWORD, FieldPurpose.of(input("passwordEmail", "passwordEmail", "text")));
    }

    @Test
    void everythingElseIsGeneric() {
        assertEquals(FieldPurpose.Purpose.GENERIC, FieldPurpose.of(input("FirstName", "FirstName", "text")));
        assertEquals(FieldPurpose.Purpose.GENERIC, FieldPurpose.of(input("addtocart_31.EnteredQuantity", "addtocart_31_EnteredQuantity", "text")));
    }

    private ElementInfo input(String name, String id, String type) {
        return new ElementInfo("input", id, name, "", type, "", true, true, "#" + id);
    }
}
