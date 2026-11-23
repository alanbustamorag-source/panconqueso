import org.levimc.launcher.core.minecraft.NativeLibraryLookup;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class NativeLibraryLookupHostTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("bodriosafio-native-lookup");
        try {
            File cache = Files.createDirectories(root.resolve("minecraft_libs/arm64")).toFile();
            File bundled = Files.createDirectories(root.resolve("launcher/arm64")).toFile();
            File cpp = new File(bundled, "libc++_shared.so");
            File http = new File(bundled, "libHttpClient.Android.so");
            Files.copy(new File(args[0], cpp.getName()).toPath(), cpp.toPath());
            Files.copy(new File(args[0], http.getName()).toPath(), http.toPath());

            // Reproduce the screenshot: Minecraft's extracted cache has no C++ runtime.
            check(!new File(cache, cpp.getName()).exists(), "fixture reproduces the missing game runtime");
            check(cpp.equals(NativeLibraryLookup.resolve(cache, bundled, cpp.getName())), "use the packaged C++ runtime when missing from Minecraft");
            check(http.equals(NativeLibraryLookup.resolve(cache, bundled, http.getName())), "use the packaged HttpClient only when needed");
            File gameCpp = new File(cache, cpp.getName());
            Files.write(gameCpp.toPath(), new byte[0]);
            check(cpp.equals(NativeLibraryLookup.resolve(cache, bundled, cpp.getName())), "an empty interrupted extraction is not a runtime");
            Files.copy(cpp.toPath(), gameCpp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            check(gameCpp.equals(NativeLibraryLookup.resolve(cache, bundled, cpp.getName())), "prefer Minecraft's runtime when available");
            check(gameCpp.equals(NativeLibraryLookup.resolve(cache, null, cpp.getName())), "game libraries do not require a bundled directory");
            for (String name : new String[] {"libminecraftpe.so", "libfmod.so", "libMediaDecoders_Android.so", "libother.so"}) {
                Files.copy(cpp.toPath(), new File(bundled, name).toPath());
                check(NativeLibraryLookup.resolve(cache, bundled, name) == null, "never substitute a game library from the launcher: " + name);
            }
            Files.delete(gameCpp.toPath());
            Files.delete(cpp.toPath());
            check(NativeLibraryLookup.resolve(cache, bundled, cpp.getName()) == null, "report an actually absent runtime");
            Files.createDirectory(cpp.toPath());
            check(NativeLibraryLookup.resolve(cache, bundled, cpp.getName()) == null, "a directory is not a runtime");
            check(NativeLibraryLookup.resolve(cache, bundled, "../libc++_shared.so") == null, "reject paths outside the selected library folders");
            check(NativeLibraryLookup.resolve(cache, bundled, null) == null, "handle unknown library names");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(root)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }
        }
        System.out.println(checks + " native library lookup checks passed (host JVM, not Android runtime)");
    }
}
