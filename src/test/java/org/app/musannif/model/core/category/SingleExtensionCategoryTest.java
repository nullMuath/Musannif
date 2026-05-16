package org.app.musannif.model.core.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingleExtensionCategoryTest {

    @Test
    void accepts_matchingExtension() {
        SingleExtensionCategory cat = new SingleExtensionCategory("pdf");
        assertTrue(cat.accepts("pdf"));
    }

    @Test
    void accepts_nonMatchingExtension() {
        SingleExtensionCategory cat = new SingleExtensionCategory("pdf");
        assertFalse(cat.accepts("docx"));
    }

    @Test
    void getName_uppercasesExtension() {
        assertEquals("PDF", new SingleExtensionCategory("pdf").getName());
        assertEquals("MP3", new SingleExtensionCategory("MP3").getName());
    }

    @Test
    void getName_blankExtension_returnsOther() {
        assertEquals("Other", new SingleExtensionCategory("").getName());
        assertEquals("Other", new SingleExtensionCategory("   ").getName());
    }

    @Test
    void accepts_normalizesToLowerCase() {
        // constructor lowercases, so accepts("PDF") should fail (we store "pdf")
        SingleExtensionCategory cat = new SingleExtensionCategory("PDF");
        assertTrue(cat.accepts("pdf"));
        assertFalse(cat.accepts("PDF"));
    }
}
