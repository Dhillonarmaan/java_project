package com.keybridge.signing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SigningPinTest {

    @Test
    void toStringIsRedacted() {
        SigningPin pin = new SigningPin("482913");
        assertFalse(pin.toString().contains("482913"));
        assertFalse(String.valueOf(pin).contains("4829"));
    }

    @Test
    void closeZeroesTheCharacters() {
        SigningPin pin = new SigningPin("482913");
        char[] chars = pin.chars();
        assertArrayEquals("482913".toCharArray(), chars);
        pin.close();
        for (char c : chars) {
            assertEquals('\0', c);
        }
    }

    @Test
    void nullOrEmptyIsEmpty() {
        assertTrue(new SigningPin(null).isEmpty());
        assertTrue(new SigningPin("").isEmpty());
        assertFalse(new SigningPin("1").isEmpty());
    }
}
