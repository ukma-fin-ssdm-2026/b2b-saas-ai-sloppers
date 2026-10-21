package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstagramHandleTest {

    @Test
    void stripsLeadingAt() {
        assertEquals("fido.naukma", InstagramHandle.normalize("@fido.naukma"));
    }
}