package org.app.musannif.model.core.category;

import org.app.musannif.model.ScannedFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileHandlerChainTest {

    private ScannedFile file(String ext, long size, Instant modified) {
        return new ScannedFile(Path.of("/f." + ext), ext, size, modified);
    }

    // -----------------------------------------------------------------------
    // ExtensionHandler
    // -----------------------------------------------------------------------

    @Test
    void extensionHandler_allowedExtension_passes() {
        ExtensionHandler h = new ExtensionHandler("pdf", "docx");
        Optional<ScannedFile> result = h.handle(file("pdf", 100L, Instant.now()));
        assertTrue(result.isPresent());
    }

    @Test
    void extensionHandler_disallowedExtension_rejects() {
        ExtensionHandler h = new ExtensionHandler("pdf");
        Optional<ScannedFile> result = h.handle(file("mp4", 100L, Instant.now()));
        assertTrue(result.isEmpty());
    }

    @Test
    void extensionHandler_emptyAllowedSet_passesAll() {
        ExtensionHandler h = new ExtensionHandler();  // empty
        Optional<ScannedFile> result = h.handle(file("anything", 100L, Instant.now()));
        assertTrue(result.isPresent());
    }

    // -----------------------------------------------------------------------
    // SizeHandler
    // -----------------------------------------------------------------------

    @Test
    void sizeHandler_withinLimit_passes() {
        SizeHandler h = new SizeHandler(1000L);
        assertTrue(h.handle(file("pdf", 999L, Instant.now())).isPresent());
        assertTrue(h.handle(file("pdf", 1000L, Instant.now())).isPresent()); // inclusive
    }

    @Test
    void sizeHandler_exceedsLimit_rejects() {
        SizeHandler h = new SizeHandler(1000L);
        assertTrue(h.handle(file("pdf", 1001L, Instant.now())).isEmpty());
    }

    @Test
    void sizeHandler_negativeMax_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new SizeHandler(-1L));
    }

    // -----------------------------------------------------------------------
    // DateHandler
    // -----------------------------------------------------------------------

    private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant JUN = Instant.parse("2024-06-01T00:00:00Z");
    private static final Instant DEC = Instant.parse("2024-12-31T00:00:00Z");

    @Test
    void dateHandler_withinRange_passes() {
        DateHandler h = new DateHandler(JAN, DEC);
        assertTrue(h.handle(file("txt", 10L, JUN)).isPresent());
    }

    @Test
    void dateHandler_beforeAfterBound_rejects() {
        DateHandler h = new DateHandler(JUN, DEC);
        assertTrue(h.handle(file("txt", 10L, JAN)).isEmpty());
    }

    @Test
    void dateHandler_afterBeforeBound_rejects() {
        DateHandler h = new DateHandler(JAN, JUN);
        assertTrue(h.handle(file("txt", 10L, DEC)).isEmpty());
    }

    @Test
    void dateHandler_noUpperBound_acceptsFutureFiles() {
        DateHandler h = new DateHandler(JAN);  // single-arg: only after
        assertTrue(h.handle(file("txt", 10L, DEC)).isPresent());
    }

    @Test
    void dateHandler_nullBothBounds_alwaysPasses() {
        DateHandler h = new DateHandler(null, null);
        assertTrue(h.handle(file("txt", 10L, Instant.now())).isPresent());
    }

    // -----------------------------------------------------------------------
    // Chained pipeline
    // -----------------------------------------------------------------------

    @Test
    void chain_extensionAndSize_bothMustPass() {
        FileHandler chain = new ExtensionHandler("pdf");
        chain.setNext(new SizeHandler(500L));

        // Good: correct ext, within size
        assertTrue(chain.handle(file("pdf", 400L, Instant.now())).isPresent());
        // Bad: wrong ext
        assertTrue(chain.handle(file("jpg", 400L, Instant.now())).isEmpty());
        // Bad: over size
        assertTrue(chain.handle(file("pdf", 600L, Instant.now())).isEmpty());
    }

    @Test
    void chain_extensionSizeDate_allMustPass() {
        FileHandler chain = new ExtensionHandler("jpg");
        chain.setNext(new SizeHandler(1000L));
        chain.setNext(new DateHandler(JAN, DEC));

        // All conditions met
        assertTrue(chain.handle(file("jpg", 500L, JUN)).isPresent());
        // Wrong extension
        assertTrue(chain.handle(file("png", 500L, JUN)).isEmpty());
        // Too large
        assertTrue(chain.handle(file("jpg", 1001L, JUN)).isEmpty());
        // Too old
        assertTrue(chain.handle(file("jpg", 500L, Instant.parse("2023-01-01T00:00:00Z"))).isEmpty());
    }

    @Test
    void setNext_returnsNext_allowsFluent() {
        ExtensionHandler ext = new ExtensionHandler("pdf");
        SizeHandler size = new SizeHandler(100L);
        FileHandler returned = ext.setNext(size);
        // setNext returns next for chaining; we just verify the chain works
        assertSame(size, returned);
    }
}
