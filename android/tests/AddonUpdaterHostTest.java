import org.levimc.launcher.bodriosafio.update.AddonChannel;
import org.levimc.launcher.bodriosafio.update.AddonInstaller;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class AddonUpdaterHostTest {
    static int checks;
    interface Test { void run() throws Exception; }
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); checks++; }
    static void rejects(Test test, String name) throws Exception {
        try { test.run(); throw new AssertionError(name); }
        catch (AssertionError e) { throw e; } catch (Exception expected) { checks++; }
    }
    static void old(Path p, String id, int[] v, String marker) throws Exception {
        Files.createDirectories(p);
        Files.writeString(p.resolve("manifest.json"), new JSONObject().put("header", new JSONObject().put("uuid", id).put("version", new JSONArray(v))).toString());
        Files.writeString(p.resolve("marker.txt"), marker);
    }
    static String refs(String id, int[] v) throws Exception {
        return new JSONArray().put(new JSONObject().put("pack_id", id).put("version", new JSONArray(v)).put("extra", "keep"))
            .put(new JSONObject().put("pack_id", "unrelated").put("version", new JSONArray(new int[]{9,9,9}))).toString();
    }
    static byte[] zip(String name) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) { z.putNextEntry(new ZipEntry(name)); z.write(1); z.closeEntry(); }
        return out.toByteArray();
    }
    static Map<String,String> snapshot(Path root) throws Exception {
        Map<String,String> out = new TreeMap<>();
        if (Files.exists(root)) try (java.util.stream.Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>)s::iterator) if (Files.isRegularFile(p) && !p.toString().contains(".bodriosafio_"))
                out.put(root.relativize(p).toString(), AddonChannel.digest(Files.readAllBytes(p)));
        }
        return out;
    }
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        String text = Files.readString(Paths.get(args[1]));
        Path temp = Files.createTempDirectory("bodrio-test-"), cache = temp.resolve("cache"), journals = temp.resolve("journals");
        AddonChannel feed = new AddonChannel(text, "android", "0.1.5"); feed.verify(bytes); checks++;
        List<Long> received = new ArrayList<>();
        byte[] streamed = AddonChannel.read(new ByteArrayInputStream(bytes), bytes.length, bytes.length, (count, total) -> {
            if (total != bytes.length || (!received.isEmpty() && count < received.get(received.size()-1)))
                throw new AssertionError("download progress must follow received bytes");
            received.add(count);
        });
        check(Arrays.equals(streamed, bytes), "progress leaves addon bytes unchanged");
        check(received.get(0) == 0 && received.get(received.size()-1) == bytes.length && received.size() > 2, "incremental download progress");
        long[] partial = {0};
        byte[] truncated = AddonChannel.read(new ByteArrayInputStream(bytes, 0, bytes.length-7), bytes.length, bytes.length,
            (count,total) -> partial[0] = count);
        check(partial[0] == bytes.length-7, "truncated transfer never reports all bytes");
        rejects(() -> feed.verify(truncated), "truncated progress cannot pass verification");
        rejects(() -> AddonChannel.read(new ByteArrayInputStream(bytes), bytes.length-1, bytes.length-1,
            (count,total) -> { if (count > total) throw new AssertionError("progress exceeded total"); }), "oversize rejected before progress");
        InputStream interrupted = new InputStream() {
            int count;
            @Override public int read() throws IOException { if (++count > 40000) throw new IOException("connection lost"); return 1; }
        };
        long[] last = {0};
        rejects(() -> AddonChannel.read(interrupted, bytes.length, bytes.length, (count,total) -> last[0] = count), "connection loss propagated");
        check(last[0] > 0 && last[0] < bytes.length, "interrupted transfer keeps partial progress");
        AddonChannel.read(new ByteArrayInputStream(bytes), bytes.length, -1, (count,total) -> {
            if (total != -1) throw new AssertionError("unknown transfer size must remain unknown");
        }); checks++;
        check(feed.version.equals(AddonChannel.display(feed.behaviorVersion)), "feed version matches the behavior pack");
        rejects(() -> feed.verify(Arrays.copyOf(bytes, bytes.length - 1)), "truncated download");
        byte[] corrupt = bytes.clone(); corrupt[100] ^= 1;
        rejects(() -> feed.verify(corrupt), "corrupt digest");
        rejects(() -> AddonChannel.validateUrl("http://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/x"), "HTTP");
        rejects(() -> AddonChannel.validateUrl("https://raw.githubusercontent.com/other/repo/main/x"), "foreign repo");
        rejects(() -> AddonChannel.validateUrl("https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/../x"), "URL traversal");
        rejects(() -> AddonChannel.validateUrl("https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/%2e%2e/x"), "encoded URL traversal");
        rejects(() -> new AddonChannel(text, "android", "0.0.0"), "old launcher");
        rejects(() -> AddonChannel.version(new JSONArray("[0,3,1.5]")), "fractional version");
        rejects(() -> AddonInstaller.prepare(zip("../escape"), cache), "zip traversal");
        rejects(() -> AddonInstaller.prepare(zip("C:/escape"), cache), "drive path");
        rejects(() -> AddonInstaller.prepare(zip("random"), cache), "missing packs");
        check(!Files.exists(temp.resolve("escape")), "no escaped file");
        try (AddonInstaller.Prepared pack = AddonInstaller.prepare(bytes, cache)) {
            check(pack.behavior.uuid.equals(AddonInstaller.BP), "nested BP");
            check(pack.resource.uuid.equals(AddonInstaller.RP), "nested RP");
            Path a = temp.resolve("a"), b = temp.resolve("b");
            List<Path> roots = Arrays.asList(a,b,a);
            check(new AddonInstaller().install(pack, roots, journals).contains(feed.version), "fresh install");
            for (Path p : Arrays.asList(a,b)) {
                check(Files.exists(p.resolve("behavior_packs/Bodriosafio_BP/scripts/main.js")), "BP installed");
                check(Files.exists(p.resolve("resource_packs/Bodriosafio_RP/manifest.json")), "RP installed");
            }
            check(new AddonInstaller().install(pack, roots, journals).equals("El addon ya está actualizado."), "idempotent");
            check(AddonInstaller.installed(Arrays.asList(a,temp.resolve("missing-storage")),0) == null, "missing storage needs update");
            Path c = temp.resolve("old"), w = c.resolve("minecraftWorlds/test");
            old(c.resolve("behavior_packs/one"), AddonInstaller.BP, new int[]{0,3,10}, "one");
            old(c.resolve("behavior_packs/two"), AddonInstaller.BP, new int[]{0,3,12}, "two");
            old(c.resolve("resource_packs/old"), AddonInstaller.RP, new int[]{1,5,12}, "rp");
            old(c.resolve("behavior_packs/unrelated"), "foreign", new int[]{1,0,0}, "untouched");
            old(w.resolve("behavior_packs/embedded"), AddonInstaller.BP, new int[]{0,3,12}, "embedded");
            Files.writeString(w.resolve("level.dat"), "WORLD");
            Files.writeString(w.resolve("world_behavior_packs.json"), refs(AddonInstaller.BP, new int[]{0,3,12}));
            Files.writeString(w.resolve("world_resource_packs.json"), refs(AddonInstaller.RP, new int[]{1,5,12}));
            new AddonInstaller().install(pack, Arrays.asList(c), journals);
            check(!Files.exists(c.resolve("behavior_packs/one")) && !Files.exists(c.resolve("behavior_packs/two")), "duplicates gone");
            check(Files.readString(c.resolve("behavior_packs/unrelated/marker.txt")).equals("untouched"), "foreign pack preserved");
            check(Files.readString(w.resolve("level.dat")).equals("WORLD"), "world data preserved");
            JSONArray active = new JSONArray(Files.readString(w.resolve("world_behavior_packs.json")));
            check(AddonChannel.compare(AddonChannel.version(active.getJSONObject(0).getJSONArray("version")), feed.behaviorVersion) == 0, "world reference upgraded");
            check(active.getJSONObject(0).getString("extra").equals("keep") && active.getJSONObject(1).getString("pack_id").equals("unrelated"), "other references preserved");
            check(Files.exists(w.resolve("behavior_packs/Bodriosafio_BP/scripts/main.js")) && !Files.exists(w.resolve("behavior_packs/embedded")), "embedded pack upgraded");
            check(Files.isDirectory(c.resolve(".bodriosafio_backups")), "backup retained");
            Path future = temp.resolve("future");
            old(future.resolve("behavior_packs/newer"), AddonInstaller.BP, new int[]{0,3,99}, "newer");
            Map<String,String> before = snapshot(future);
            rejects(() -> new AddonInstaller().install(pack, Arrays.asList(future), journals), "downgrade");
            check(snapshot(future).equals(before), "newer unchanged");
            Path f = temp.resolve("failure"), fw = f.resolve("minecraftWorlds/test");
            old(f.resolve("behavior_packs/old"), AddonInstaller.BP, new int[]{0,3,12}, "original");
            Files.createDirectories(fw); Files.writeString(fw.resolve("world_behavior_packs.json"), refs(AddonInstaller.BP, new int[]{0,3,12}));
            Map<String,String> failBefore = snapshot(f);
            rejects(() -> new AddonInstaller(n -> { if (n.equals("world-reference")) throw new java.io.IOException("injected"); }).install(pack, Arrays.asList(f), journals), "IO failure");
            check(snapshot(f).equals(failBefore), "packs and refs restored");
            Path crash = temp.resolve("crash");
            old(crash.resolve("behavior_packs/old"), AddonInstaller.BP, new int[]{0,3,12}, "crash");
            before = snapshot(crash);
            try { new AddonInstaller(n -> { if (n.equals("pack")) throw new AssertionError("crash"); }).install(pack, Arrays.asList(crash), journals); throw new Exception("no crash"); }
            catch (AssertionError expected) { checks++; }
            AddonInstaller.recover(Arrays.asList(crash), journals);
            check(snapshot(crash).equals(before), "crash recovery");
            Path conflict = temp.resolve("conflict");
            old(conflict.resolve("behavior_packs/Bodriosafio_BP"), "someone-else", new int[]{0,0,1}, "foreign");
            before = snapshot(conflict);
            rejects(() -> new AddonInstaller().install(pack, Arrays.asList(conflict), journals), "foreign destination");
            check(snapshot(conflict).equals(before), "foreign destination unchanged");
            Path first = temp.resolve("cross-a"), second = temp.resolve("cross-b");
            old(first.resolve("behavior_packs/old"), AddonInstaller.BP, new int[]{0,3,12}, "first");
            old(second.resolve("behavior_packs/old"), AddonInstaller.BP, new int[]{0,3,12}, "second");
            Map<String,String> firstBefore = snapshot(first), secondBefore = snapshot(second); int[] count = {0};
            rejects(() -> new AddonInstaller(n -> { if (n.equals("pack") && ++count[0] == 3) throw new java.io.IOException("second root"); }).install(pack, Arrays.asList(first,second), journals), "second root failure");
            check(snapshot(first).equals(firstBefore) && snapshot(second).equals(secondBefore), "both storages restored");
        }
        System.out.println("PASS: " + checks + " updater checks: real archives, world references, rollback and recovery");
    }
}
