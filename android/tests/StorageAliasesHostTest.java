import org.levimc.launcher.bodriosafio.update.AddonChannel;
import org.levimc.launcher.bodriosafio.update.AddonInstaller;
import org.levimc.launcher.bodriosafio.update.StoragePaths;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Exercise the real addon installer through an Android-like app-directory alias. */
public final class StorageAliasesHostTest {
    static int checks;
    interface Job { void run() throws Exception; }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    static void rejects(Job operation, String message) throws Exception {
        try { operation.run(); throw new AssertionError(message); }
        catch (IOException expected) { checks++; }
    }
    static void oldPack(Path directory, String uuid, int[] version) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("manifest.json"), new JSONObject().put("header",
            new JSONObject().put("uuid", uuid).put("version", new JSONArray(version))).toString());
        Files.writeString(directory.resolve("original.txt"), "original");
    }
    static Map<String,String> snapshot(Path root) throws Exception {
        Map<String,String> result = new TreeMap<>();
        if (Files.isDirectory(root)) try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>)stream::iterator)
                if (Files.isRegularFile(p) && !p.toString().contains(".bodriosafio_"))
                    result.put(root.relativize(p).toString(), AddonChannel.digest(Files.readAllBytes(p)));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        byte[] archive = Files.readAllBytes(Paths.get(args[0]));
        Path temp = Files.createTempDirectory("bodriosafio-storage-alias-");
        Path real = Files.createDirectories(temp.resolve("real-app"));
        Files.createDirectories(real.resolve("cache"));
        Files.createDirectories(real.resolve("files"));
        Path alias = Files.createSymbolicLink(temp.resolve("android-app-alias"), real);
        Path first = real.resolve("profile-internal"), second = temp.resolve("profile-external");
        List<Path> roots = Arrays.asList(first, second);
        // The old Android call sites pass aliased paths directly to the installer.
        try (AddonInstaller.Prepared pack = AddonInstaller.prepare(archive, alias.resolve("cache/old-flow"))) {
            try {
                new AddonInstaller().install(pack, roots, alias.resolve("files/old-journals"));
                throw new AssertionError("Old alias path should reproduce the reported failure");
            } catch (IOException expected) {
                check(expected.getMessage().contains("contiene un enlace"), "same reported storage error");
            }
        } catch (IOException expectedOnClose) {
            check(expectedOnClose.getMessage().contains("contiene un enlace"), "old cache cleanup also rejects alias");
        }
        check(!Files.exists(first.resolve("behavior_packs/Bodriosafio_BP")), "old failure moved no active pack");
        Path cache = StoragePaths.privateChild(alias.resolve("cache").toFile(), "bodriosafio-native-addon");
        Path journals = StoragePaths.privateChild(alias.resolve("files").toFile(), "bodriosafio-update-journals");
        check(cache.startsWith(real) && journals.startsWith(real), "trusted bases resolved before child selection");
        Path world = Files.createDirectories(first.resolve("minecraftWorlds/test"));
        Files.writeString(world.resolve("level.dat"), "KEEP THE WORLD");
        try (AddonInstaller.Prepared pack = AddonInstaller.prepare(archive, cache)) {
            check(new AddonInstaller().install(pack, roots, journals).contains("0.3.15"), "installation through system alias succeeds");
            for (Path root : roots) {
                check(Files.isRegularFile(root.resolve("behavior_packs/Bodriosafio_BP/scripts/main.js")), "BP installed");
                check(Files.isRegularFile(root.resolve("resource_packs/Bodriosafio_RP/manifest.json")), "RP installed");
            }
            check(Files.readString(world.resolve("level.dat")).equals("KEEP THE WORLD"), "world data preserved");
            check(new AddonInstaller().install(pack, roots, journals).equals("El addon ya está actualizado."), "repeat launch is idempotent");
            // Keep rejecting links inside the installer-owned child and pack trees.
            Path outside = Files.createDirectories(temp.resolve("outside"));
            Files.writeString(outside.resolve("sentinel.txt"), "DO NOT TOUCH");
            Map<String,String> outsideBefore = snapshot(outside);
            Files.createSymbolicLink(real.resolve("cache/linked-child"), outside);
            rejects(() -> StoragePaths.privateChild(alias.resolve("cache").toFile(), "linked-child"), "linked cache child rejected");
            Path hostile = Files.createDirectories(temp.resolve("hostile-profile"));
            Files.createSymbolicLink(hostile.resolve("behavior_packs"), outside);
            rejects(() -> new AddonInstaller().install(pack, Arrays.asList(hostile), journals), "pack link still rejected");
            check(snapshot(outside).equals(outsideBefore), "linked destination untouched");
            rejects(() -> StoragePaths.privateChild(alias.resolve("files").toFile(), "../outside"), "relative traversal rejected");
            rejects(() -> StoragePaths.privateChild(alias.resolve("files").toFile(), "/outside"), "absolute child rejected");
            // Recovery uses the same canonical journal path after a simulated process death.
            Path crash = temp.resolve("crash-profile");
            oldPack(crash.resolve("behavior_packs/old"), AddonInstaller.BP, new int[]{0,3,12});
            Map<String,String> before = snapshot(crash);
            try {
                new AddonInstaller(step -> { if (step.equals("pack")) throw new AssertionError("process death"); })
                    .install(pack, Arrays.asList(crash), journals);
                throw new IOException("Expected simulated process death");
            } catch (AssertionError expected) { checks++; }
            AddonInstaller.recover(Arrays.asList(crash), StoragePaths.privateChild(alias.resolve("files").toFile(), "bodriosafio-update-journals"));
            check(snapshot(crash).equals(before), "recovery through aliased Context directory restores original pack");
        }
        try (java.util.stream.Stream<Path> stream = Files.list(cache)) {
            check(stream.noneMatch(p -> p.getFileName().toString().startsWith("bodriosafio-pack-")), "new temporary pack cleaned normally");
        }
        System.out.println("PASS: " + checks + " storage-alias checks; original failure reproduced, real BP/RP install and recovery pass");
    }
}
