package org.app.musannif.model.core.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoriesTest {

    // -----------------------------------------------------------------------
    // Documents
    // -----------------------------------------------------------------------

    @Test
    void documents_acceptsKnownExtensions() {
        FileCategory cat = new Categories.Documents();
        for (String ext : new String[]{"pdf", "doc", "docx", "odt", "rtf", "txt",
                "tex", "md", "csv", "xls", "xlsx", "ods", "ppt", "pptx", "odp"}) {
            assertTrue(cat.accepts(ext), "Expected Documents to accept: " + ext);
        }
    }

    @Test
    void documents_rejectsUnknownExtensions() {
        FileCategory cat = new Categories.Documents();
        assertFalse(cat.accepts("mp4"));
        assertFalse(cat.accepts("jpg"));
        assertFalse(cat.accepts(""));
    }

    @Test
    void documents_name() {
        assertEquals("Documents", new Categories.Documents().getName());
    }

    // -----------------------------------------------------------------------
    // Images
    // -----------------------------------------------------------------------

    @Test
    void images_acceptsKnownExtensions() {
        FileCategory cat = new Categories.Images();
        for (String ext : new String[]{"jpg", "jpeg", "png", "gif", "bmp", "webp",
                "svg", "tiff", "tif", "ico", "heic", "heif", "raw", "cr2", "nef", "avif"}) {
            assertTrue(cat.accepts(ext), "Expected Images to accept: " + ext);
        }
    }

    @Test
    void images_rejectsDocumentExtensions() {
        FileCategory cat = new Categories.Images();
        assertFalse(cat.accepts("pdf"));
        assertFalse(cat.accepts("mp3"));
    }

    @Test
    void images_name() {
        assertEquals("Images", new Categories.Images().getName());
    }

    // -----------------------------------------------------------------------
    // Videos
    // -----------------------------------------------------------------------

    @Test
    void videos_acceptsKnownExtensions() {
        FileCategory cat = new Categories.Videos();
        for (String ext : new String[]{"mp4", "mkv", "avi", "mov", "wmv", "flv",
                "webm", "m4v", "mpeg", "mpg", "3gp", "ogv", "ts", "vob"}) {
            assertTrue(cat.accepts(ext), "Expected Videos to accept: " + ext);
        }
    }

    @Test
    void videos_rejectsAudioExtensions() {
        assertFalse(new Categories.Videos().accepts("mp3"));
    }

    @Test
    void videos_name() {
        assertEquals("Videos", new Categories.Videos().getName());
    }

    // -----------------------------------------------------------------------
    // Audio
    // -----------------------------------------------------------------------

    @Test
    void audio_acceptsKnownExtensions() {
        FileCategory cat = new Categories.Audio();
        for (String ext : new String[]{"mp3", "wav", "flac", "aac", "ogg", "wma",
                "m4a", "opus", "aiff", "alac", "mid", "midi", "amr"}) {
            assertTrue(cat.accepts(ext), "Expected Audio to accept: " + ext);
        }
    }

    @Test
    void audio_name() {
        assertEquals("Audio", new Categories.Audio().getName());
    }

    // -----------------------------------------------------------------------
    // Archives
    // -----------------------------------------------------------------------

    @Test
    void archives_acceptsKnownExtensions() {
        FileCategory cat = new Categories.Archives();
        for (String ext : new String[]{"zip", "tar", "gz", "bz2", "xz", "7z",
                "rar", "iso", "tgz", "cab", "deb", "rpm", "jar", "war"}) {
            assertTrue(cat.accepts(ext), "Expected Archives to accept: " + ext);
        }
    }

    @Test
    void archives_name() {
        assertEquals("Archives", new Categories.Archives().getName());
    }

    // -----------------------------------------------------------------------
    // ExtensionCategory base
    // -----------------------------------------------------------------------

    @Test
    void extensionCategory_caseInsensitiveStoredAsProvided() {
        // extensions are stored as-is; callers should pass lowercase
        FileCategory cat = new Categories.Images();
        assertFalse(cat.accepts("JPG")); // stored as lowercase; uppercase shouldn't match
    }
}
