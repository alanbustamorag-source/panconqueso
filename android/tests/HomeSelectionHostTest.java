package org.levimc.launcher.bodriosafio;

import java.io.File;
import java.util.List;
import org.levimc.launcher.core.versions.GameVersion;

/** Use the launcher classes and android.jar from the release build on the host JVM. */
public class HomeSelectionHostTest {
    private static GameVersion profile(String name, String version, boolean installed, String pkg) {
        return new GameVersion(name, name, version, new File(name), installed, pkg, "arm64-v8a");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        GameVersion current = profile("installed", "1.26.66.1", true, "com.mojang.minecraftpe");
        GameVersion preview = profile("preview", "1.26.70", true, "com.mojang.minecraftpreview");
        GameVersion imported = profile("survival", "1.26.60.4", false, null);
        List<GameVersion> installed = List.of(preview, current);
        List<GameVersion> imports = List.of(imported);
        check(BodriosafioProfile.forHome(imported, installed, imports) == imported, "keep the instance selected in Tools");
        check(BodriosafioProfile.forHome(current, installed, imports) == current, "keep current installation when selected");
        check(BodriosafioProfile.forHome(null, installed, imports) == current, "a first launch uses the official package");
        check(BodriosafioProfile.forHome(imported, installed, List.of()) == current, "ignore stale selection after deleting an import");
        check(BodriosafioProfile.forHome(imported, List.of(preview), imports) == null, "preview does not replace the official installation");
        check(BodriosafioProfile.forHome(imported, List.of(), imports) == null, "do not select a profile when the official installation is gone");
        System.out.println("PASS: 6 home profile selection checks");
    }
}
