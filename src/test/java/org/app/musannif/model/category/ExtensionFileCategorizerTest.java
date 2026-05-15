package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionFileCategorizerTest {

    private ScannedFile file(String ext) {
        return new ScannedFile(Path.of("/f." + ext), ext, 100L, Instant.now());
    }

    private ScannedFile sizedFile(String ext, long size) {
        return new ScannedFile(Path.of("/f." + ext), ext, size, Instant.now());
    }

    private ExtensionFileCategorizer defaultCategorizer() {
        return new ExtensionFileCategorizer.Builder()
                .register(new Categories.Documents())
                .register(new Categories.Images())
                .register(new Categories.Videos())
                .register(new Categories.Audio())
                .register(new Categories.Archives())
                .build();
    }

    @Test
    void categorize_knowExtension_placedinCorrectBucket() {
        ExtensionFileCategorizer cat = defaultCategorizer();
        Map<String, List<ScannedFile>> result = cat.categorize(List.of(file("pdf"), file("jpg")));

        assertEquals(1, result.get("Documents").size());
        assertEquals(1, result.get("Images").size());
        assertEquals(0, result.get("Videos").size());
    }

    @Test
    void categorize_unknownExtension_goesToOther() {
        ExtensionFileCategorizer cat = defaultCategorizer();
        Map<String, List<ScannedFile>> result = cat.categorize(List.of(file("xyz")));

        assertEquals(1, result.get(ExtensionFileCategorizer.FALLBACK_CATEGORY).size());
    }

    @Test
    void categorize_emptyInput_allBucketsEmpty() {
        ExtensionFileCategorizer cat = defaultCategorizer();
        Map<String, List<ScannedFile>> result = cat.categorize(List.of());

        result.values().forEach(list -> assertEquals(0, list.size()));
    }

    @Test
    void categorize_stream_sameResultAsCollection() {
        ExtensionFileCategorizer cat = defaultCategorizer();
        List<ScannedFile> files = List.of(file("pdf"), file("mp3"), file("unknown"));
        Map<String, List<ScannedFile>> fromCollection = cat.categorize(files);
        Map<String, List<ScannedFile>> fromStream = cat.categorize(Stream.of(file("pdf"), file("mp3"), file("unknown")));

        assertEquals(fromCollection.get("Documents").size(), fromStream.get("Documents").size());
        assertEquals(fromCollection.get("Audio").size(), fromStream.get("Audio").size());
    }

    @Test
    void fallbackCategory_constant() {
        assertEquals("Other", ExtensionFileCategorizer.FALLBACK_CATEGORY);
    }

    @Test
    void builder_nullCategory_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new ExtensionFileCategorizer.Builder().register(null));
    }

    @Test
    void categorize_withSizeDecorator_filtersLargeFiles() {
        // Only documents ≤ 500 bytes
        FileCategory smallDocs = new SizeFilterDecorator(new Categories.Documents(), 500L);
        ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                .register(smallDocs)
                .build();

        ScannedFile small = sizedFile("pdf", 400L);
        ScannedFile large = sizedFile("pdf", 600L);

        Map<String, List<ScannedFile>> result = cat.categorize(List.of(small, large));
        assertEquals(1, result.get("Documents").size());
        assertEquals(1, result.get(ExtensionFileCategorizer.FALLBACK_CATEGORY).size());
    }

    @Test
    void categorize_withDateDecorator_filtersOldFiles() {
        Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
        FileCategory recentImages = new DateFilterDecorator(new Categories.Images(), cutoff);
        ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                .register(recentImages)
                .build();

        ScannedFile old = new ScannedFile(Path.of("/old.jpg"), "jpg", 100L,
                Instant.parse("2023-06-01T00:00:00Z"));
        ScannedFile recent = new ScannedFile(Path.of("/new.jpg"), "jpg", 100L,
                Instant.parse("2024-06-01T00:00:00Z"));

        Map<String, List<ScannedFile>> result = cat.categorize(List.of(old, recent));
        assertEquals(1, result.get("Images").size());
        assertEquals(1, result.get(ExtensionFileCategorizer.FALLBACK_CATEGORY).size());
    }

    @Test
    void firstMatchWins_overlappingCategories() {
        // Both categories accept "pdf"; first registered should win
        FileCategory cat1 = new Categories.Documents(); // accepts pdf
        FileCategory cat2 = new SingleExtensionCategory("pdf"); // also accepts pdf
        ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                .register(cat1)
                .register(cat2)
                .build();

        Map<String, List<ScannedFile>> result = cat.categorize(List.of(file("pdf")));
        assertEquals(1, result.get("Documents").size());
        assertEquals(0, result.get("PDF").size());
    }
}
