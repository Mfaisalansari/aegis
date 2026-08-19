package com.aegis.web.form;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormBodyParserTest {

    @Test
    void decodesSimpleKeyValuePairs() {

        Map<String, String> values = FormBodyParser.parse("baseUrl=https%3A%2F%2Fexample.com&name=Test".getBytes(StandardCharsets.UTF_8));

        assertEquals("https://example.com", values.get("baseUrl"));
        assertEquals("Test", values.get("name"));
    }

    @Test
    void decodesPlusAsSpace() {

        Map<String, String> values = FormBodyParser.parse("description=Autonomous+exploration+run".getBytes(StandardCharsets.UTF_8));

        assertEquals("Autonomous exploration run", values.get("description"));
    }

    @Test
    void keyWithNoEqualsSignIsPresentWithEmptyValue() {

        Map<String, String> values = FormBodyParser.parse("headless".getBytes(StandardCharsets.UTF_8));

        assertTrue(values.containsKey("headless"));
        assertEquals("", values.get("headless"));
    }

    @Test
    void emptyBodyProducesEmptyMap() {
        assertTrue(FormBodyParser.parse(new byte[0]).isEmpty());
    }

    @Test
    void repeatedKeyKeepsTheLastValue() {

        Map<String, String> values = FormBodyParser.parse("strategy=greedy&strategy=adaptive".getBytes(StandardCharsets.UTF_8));

        assertEquals("adaptive", values.get("strategy"));
    }

    @Test
    void decodesMultiLineTextareaNewlines() {

        Map<String, String> values = FormBodyParser.parse("noiseDenyPatterns=first%0D%0Asecond".getBytes(StandardCharsets.UTF_8));

        assertEquals("first\r\nsecond", values.get("noiseDenyPatterns"));
    }
}
