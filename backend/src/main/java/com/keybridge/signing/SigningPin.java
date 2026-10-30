package com.keybridge.signing;

import java.util.Arrays;

/**
 * A PIN typed by the user for one signing request. It is never stored:
 * toString() is redacted so it cannot reach logs or audit_logs, and
 * close() zeroes the characters once signing is done.
 */
public final class SigningPin implements AutoCloseable {

    private final char[] value;

    /** Lets Spring bind {@code @RequestParam SigningPin pin} directly. */
    public SigningPin(String value) {
        this.value = value == null ? new char[0] : value.toCharArray();
    }

    public boolean isEmpty() {
        return value.length == 0;
    }

    /** The PIN characters. Callers must not copy or keep them. */
    public char[] chars() {
        return value;
    }

    @Override
    public void close() {
        Arrays.fill(value, '\0');
    }

    @Override
    public String toString() {
        return "SigningPin[****]";
    }
}
