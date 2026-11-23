// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolve only a trusted Context directory before adding an installer-owned child. */
public final class StoragePaths {
    private StoragePaths() {}

    public static Path privateChild(File contextDirectory, String child) throws IOException {
        if (contextDirectory == null || child == null || child.isEmpty()
                || child.equals(".") || child.equals("..")
                || child.indexOf('/') >= 0 || child.indexOf('\\') >= 0)
            throw new IOException("No se pudo determinar la carpeta del launcher.");
        // Android may expose app-private storage through a system-managed alias.
        // Resolve the trusted base, never an arbitrary pack/world/journal path.
        Path base = contextDirectory.getCanonicalFile().toPath();
        Path destination = base.resolve(child);
        if (Files.isSymbolicLink(destination))
            throw new IOException("La carpeta del launcher contiene un enlace; no se modificó.");
        return destination;
    }
}
