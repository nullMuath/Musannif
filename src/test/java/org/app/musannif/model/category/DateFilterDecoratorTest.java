package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DateFilterDecoratorTest {

    private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant JUN = Instant.parse("2024-06-01T00:00:00Z");
    private static final Instant DEC = Instant.parse("2024-12-31T23:59:59Z");

    private ScannedFile file(String ext, Instant modified) {
        return new ScannedFile(Path.of("/f." + ext), ext, 100L, modified);
    }

    @Test
    void accepts_fileWithinBothBounds() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, DEC);
        assertTrue(dec.accepts(file("jpg", JUN)));
    }

    @Test
    void rejects_fileTooEarly() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JUN, DEC);
        assertFalse(dec.accepts(file("jpg", JAN)));  // before 'after'
    }

    @Test
    void rejects_fileTooLate() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, JUN);
        assertFalse(dec.accepts(file("jpg", DEC)));  // after 'before'
    }

    @Test
    void accepts_fileAtExactAfterBound() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, DEC);
        assertTrue(dec.accepts(file("jpg", JAN)));  // isBefore(JAN) == false → passes
    }

    @Test
    void noUpperBound_acceptsAnyFutureFile() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN);
        assertTrue(dec.accepts(file("jpg", DEC)));
        assertTrue(dec.accepts(file("jpg", JAN)));
    }

    @Test
    void nullAfter_noLowerBound() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), null, JUN);
        assertTrue(dec.accepts(file("jpg", JAN)));
        assertFalse(dec.accepts(file("jpg", DEC)));
    }

    @Test
    void rejects_wrongExtension() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, DEC);
        assertFalse(dec.accepts(file("pdf", JUN)));
    }

    @Test
    void accepts_extensionOnly_delegates() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, DEC);
        assertTrue(dec.accepts("jpg"));
        assertFalse(dec.accepts("zip"));
    }

    @Test
    void getAfterAndBefore_returnConfiguredValues() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, DEC);
        assertEquals(JAN, dec.getAfter());
        assertEquals(DEC, dec.getBefore());
    }

    @Test
    void singleArgCtor_requiresNonNullAfter() {
        assertThrows(NullPointerException.class,
                () -> new DateFilterDecorator(new Categories.Images(), (Instant) null));
    }

    @Test
    void getName_delegatesToWrapped() {
        DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN);
        assertEquals("Images", dec.getName());
    }
}
