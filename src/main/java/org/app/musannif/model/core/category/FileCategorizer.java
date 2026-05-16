package org.app.musannif.model.core.category;

import org.app.musannif.model.ScannedFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FileCategorizer {
    Map<String, List<ScannedFile>> categorize(Collection<ScannedFile> files);
}
