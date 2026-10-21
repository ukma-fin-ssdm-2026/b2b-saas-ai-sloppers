package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstagramHandleTest {

    @Test
    void stripsLeadingAt() {
        assertEquals("fido_naukma", InstagramHandle.normalize("@fido_naukma"));
    }

    @Test
    void trimsAndLowercases() {
        assertEquals("fido_naukma", InstagramHandle.normalize("  @FIdo_NaUKMA "));
    }
}