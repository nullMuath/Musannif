package org.app.musannif.model.core.command;

import java.io.IOException;

public interface FileCommand {
    void execute() throws IOException;
    void undo() throws IOException;
}
