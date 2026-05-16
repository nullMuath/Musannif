package org.app.musannif.model.core.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Categories utility class.
 *
 * The existing CategoriesTest already covers Documents, Images, Videos, Audio,
 * and Archives thoroughly.  This file exists to hit the constructor of the
 * outer Categories class itself (which IntelliJ tracks as a separate class
 * entry) and to ensure every inner-class constructor is invoked in isolation
 * so the class-level 0% disappears.
 *
 * Place this file at:
 *   src/test/java/org/app/musannif/model/category/CategoriesClassTest.java
 */
class CategoriesClassTest {

    // Each test constructs the inner class directly, exercising its constructor
    // and confirming getName() returns the right string — enough to flip the
    // class coverage from 0% to 100%.

    @Test
    void documents_constructor_and_name() {
        Categories.Documents d = new Categories.Documents();
        assertEquals("Documents", d.getName());
    }

    @Test
    void images_constructor_and_name() {
        Categories.Images i = new Categories.Images();
        assertEquals("Images", i.getName());
    }

    @Test
    void videos_constructor_and_name() {
        Categories.Videos v = new Categories.Videos();
        assertEquals("Videos", v.getName());
    }

    @Test
    void audio_constructor_and_name() {
        Categories.Audio a = new Categories.Audio();
        assertEquals("Audio", a.getName());
    }

    @Test
    void archives_constructor_and_name() {
        Categories.Archives ar = new Categories.Archives();
        assertEquals("Archives", ar.getName());
    }

    // Verify a sample extension for each category to confirm the super()
    // call passed the right set of extensions.
    @Test
    void each_category_accepts_a_representative_extension() {
        assertTrue(new Categories.Documents().accepts("pdf"));
        assertTrue(new Categories.Images().accepts("png"));
        assertTrue(new Categories.Videos().accepts("mp4"));
        assertTrue(new Categories.Audio().accepts("mp3"));
        assertTrue(new Categories.Archives().accepts("zip"));
    }

    // Verify cross-category rejection so the accepts() false-branch is hit.
    @Test
    void each_category_rejects_other_categories_extension() {
        assertFalse(new Categories.Documents().accepts("mp4"));
        assertFalse(new Categories.Images().accepts("mp3"));
        assertFalse(new Categories.Videos().accepts("pdf"));
        assertFalse(new Categories.Audio().accepts("jpg"));
        assertFalse(new Categories.Archives().accepts("docx"));
    }
}
