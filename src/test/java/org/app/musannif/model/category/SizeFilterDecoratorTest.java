package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SizeFilterDecoratorTest {

    private ScannedFile file(String ext, long size) {
        return new ScannedFile(Path.of("/f." + ext), ext, size, Instant.now());
    }

    @Test
    void accepts_fileWithinLimit() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Documents(), 1000L);
        assertTrue(dec.accepts(file("pdf", 999L)));
        assertTrue(dec.accepts(file("pdf", 1000L)));  // boundary: inclusive
    }

    @Test
    void rejects_fileExceedingLimit() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Documents(), 1000L);
        assertFalse(dec.accepts(file("pdf", 1001L)));
    }

    @Test
    void rejects_wrongExtension_regardlessOfSize() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Documents(), 9999L);
        assertFalse(dec.accepts(file("mp4", 100L)));
    }

    @Test
    void accepts_extensionOnly_delegates() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Documents(), 1000L);
        assertTrue(dec.accepts("pdf"));   // extension-only delegates
        assertFalse(dec.accepts("mp4"));
    }

    @Test
    void getName_delegatesToWrapped() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Images(), 1000L);
        assertEquals("Images", dec.getName());
    }

    @Test
    void getMaxSizeBytes_returnsConfiguredLimit() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Documents(), 5000L);
        assertEquals(5000L, dec.getMaxSizeBytes());
    }

    @Test
    void negativeMaxSize_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new SizeFilterDecorator(new Categories.Documents(), -1L));
    }

    @Test
    void zeroMaxSize_acceptsZeroByteFile() {
        SizeFilterDecorator dec = new SizeFilterDecorator(new Categories.Documents(), 0L);
        assertTrue(dec.accepts(file("pdf", 0L)));
        assertFalse(dec.accepts(file("pdf", 1L)));
    }
}
