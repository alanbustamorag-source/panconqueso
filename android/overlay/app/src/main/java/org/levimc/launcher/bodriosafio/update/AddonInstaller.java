// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio.update;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs only the two stable Bodriosafío identities. Independent of Android for host testing. */
public final class AddonInstaller {
    public static final String BP = "0c6db7ac-4c6f-4942-bae6-c16b5065036b";
    public static final String RP = "1d763fb9-7cb5-429f-9789-b4b16e2301d1";
    private static final String[] IDS = {BP, RP}, DIRS = {"behavior_packs", "resource_packs"};
    private static final int MAX_JSON = 1024 * 1024;
    private static final long MAX_EXPANDED = 256L * 1024 * 1024;
    private static final int MAX_FILES = 20000;

    public interface Step { void reached(String name) throws Exception; }
    private final Step step;
    public AddonInstaller() { this(name -> {}); }
    public AddonInstaller(Step step) { this.step = step; }

    public static final class Pack {
        public final Path directory;
        public final String uuid;
        public final int[] version;
        public final JSONObject manifest;
        Pack(Path path) throws Exception {
            directory = path;
            manifest = object(path.resolve("manifest.json"));
            JSONObject header = manifest.getJSONObject("header");
            uuid = header.getString("uuid").toLowerCase(java.util.Locale.ROOT);
            version = AddonChannel.version(header.getJSONArray("version"));
        }
    }

    public static final class Prepared implements AutoCloseable {
        public final Pack behavior, resource;
        private final Path temp;
        Prepared(Path temp, Pack bp, Pack rp) { this.temp = temp; behavior = bp; resource = rp; }
        public Pack pack(int i) { return i == 0 ? behavior : resource; }
        @Override public void close() throws IOException { delete(temp); }
    }

    public static Prepared prepare(byte[] archive, Path cache) throws Exception {
        if (archive.length == 0 || archive.length > AddonChannel.MAX_ARCHIVE) throw new IOException("El archivo del addon no tiene un tamaño válido.");
        Files.createDirectories(cache);
        Path temp = Files.createTempDirectory(cache, "bodriosafio-pack-");
        try {
            int[] count = {0}; long[] total = {0};
            extract(new ByteArrayInputStream(archive), temp.resolve("unpacked"), count, total);
            List<Path> nested = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.walk(temp.resolve("unpacked"))) {
                stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".mcpack")).forEach(nested::add);
            }
            int i = 0;
            for (Path pack : nested) {
                try (InputStream input = Files.newInputStream(pack)) { extract(input, temp.resolve("nested-" + i++), count, total); }
            }
            Pack bp = null, rp = null;
            List<Path> manifests = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.walk(temp)) {
                stream.filter(p -> p.getFileName().toString().equals("manifest.json")).forEach(manifests::add);
            }
            for (Path manifest : manifests) {
                Pack p = new Pack(manifest.getParent());
                if (BP.equals(p.uuid)) { if (bp != null) throw new IOException("El archivo repite el behavior pack."); bp = p; }
                else if (RP.equals(p.uuid)) { if (rp != null) throw new IOException("El archivo repite el resource pack."); rp = p; }
                else throw new IOException("El archivo incluye un pack que no pertenece a Bodriosafío.");
            }
            if (bp == null || rp == null) throw new IOException("La actualización debe incluir el BP y el RP de Bodriosafío.");
            requireModule(bp, "data"); requireModule(rp, "resources");
            JSONArray dependencies = bp.manifest.getJSONArray("dependencies"); boolean linked = false;
            for (int n = 0; n < dependencies.length(); n++) {
                JSONObject dependency = dependencies.getJSONObject(n);
                if (RP.equalsIgnoreCase(dependency.optString("uuid", ""))) {
                    if (linked || AddonChannel.compare(AddonChannel.version(dependency.getJSONArray("version")), rp.version) != 0)
                        throw new IOException("El BP y el RP no corresponden a la misma actualización.");
                    linked = true;
                }
            }
            if (!linked) throw new IOException("Falta la dependencia entre los packs de Bodriosafío.");
            return new Prepared(temp, bp, rp);
        } catch (Exception | Error error) { delete(temp); throw error; }
    }

    private static void requireModule(Pack pack, String type) throws Exception {
        JSONArray modules = pack.manifest.getJSONArray("modules"); boolean found = false;
        for (int i = 0; i < modules.length(); i++) {
            JSONObject m = modules.getJSONObject(i);
            if (type.equals(m.getString("type"))) found = true;
            if (AddonChannel.compare(AddonChannel.version(m.getJSONArray("version")), pack.version) != 0)
                throw new IOException("La versión de un módulo no coincide con su pack.");
        }
        if (!found) throw new IOException("El tipo de pack no es correcto.");
        int[] minimum = AddonChannel.version(pack.manifest.getJSONObject("header").getJSONArray("min_engine_version"));
        // Minecraft's installed version is presented as 26.60, while pack manifests use 1.26.60.
        if (AddonChannel.compare(minimum, new int[] {1, 26, 60}) > 0) throw new IOException("El addon necesita una versión de Minecraft posterior a 26.60.");
    }

    private static void extract(InputStream input, Path target, int[] count, long[] total) throws Exception {
        Files.createDirectories(target);
        Set<String> seen = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (++count[0] > MAX_FILES || name.startsWith("/") || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0)
                    throw new IOException("El archivo contiene rutas o cantidades de archivos no válidas.");
                Path destination = target.resolve(name).normalize();
                if (!destination.startsWith(target) || destination.equals(target) || !seen.add(destination.toString().toLowerCase(java.util.Locale.ROOT)))
                    throw new IOException("El archivo contiene rutas repetidas o fuera del pack.");
                if (entry.isDirectory()) { Files.createDirectories(destination); continue; }
                Files.createDirectories(destination.getParent());
                try (java.io.OutputStream out = Files.newOutputStream(destination)) {
                    byte[] buffer = new byte[16384]; int n;
                    while ((n = zip.read(buffer)) != -1) {
                        total[0] += n;
                        if (total[0] > MAX_EXPANDED) throw new IOException("El addon descomprimido supera el tamaño permitido.");
                        out.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    public static int[] installed(List<Path> roots, int type) throws Exception {
        int[] oldestStorage = null;
        for (Path root : roots) {
            int[] newest = null;
            for (Path p : owned(root.resolve(DIRS[type]), IDS[type])) {
                int[] v = new Pack(p).version;
                if (newest == null || AddonChannel.compare(v, newest) > 0) newest = v;
            }
            if (newest == null) return null;
            if (oldestStorage == null || AddonChannel.compare(newest, oldestStorage) < 0) oldestStorage = newest;
        }
        return oldestStorage;
    }

    private static List<Path> owned(Path parent, String uuid) throws Exception {
        List<Path> paths = new ArrayList<>();
        if (!Files.isDirectory(parent)) return paths;
        rejectLinks(parent);
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            for (Path p : stream) {
                if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(p.resolve("manifest.json"), LinkOption.NOFOLLOW_LINKS)) continue;
                JSONObject header;
                try { header = object(p.resolve("manifest.json")).getJSONObject("header"); }
                catch (Exception e) { continue; } // Unrelated, damaged packs are never modified.
                if (uuid.equalsIgnoreCase(header.optString("uuid", ""))) { rejectLinks(p); paths.add(p); }
            }
        }
        return paths;
    }

    /** A journal is written before any active pack or world reference is moved. */
    public String install(Prepared update, List<Path> requestedRoots, Path journals) throws Exception {
        synchronized (AddonInstaller.class) { return installLocked(update, requestedRoots, journals); }
    }

    private String installLocked(Prepared update, List<Path> requestedRoots, Path journals) throws Exception {
        List<Path> roots = canonicalRoots(requestedRoots);
        recover(roots, journals);
        Files.createDirectories(journals);
        String id = "update-" + UUID.randomUUID();
        Path journalFile = journals.resolve(id + ".json");
        JSONObject journal = new JSONObject();
        JSONArray old = new JSONArray(), fresh = new JSONArray(), references = new JSONArray();
        journal.put("schema", 1).put("state", "pending").put("old", old).put("fresh", fresh).put("references", references);
        List<Path> staging = new ArrayList<>();
        try {
            for (Path root : roots) {
                Files.createDirectories(root);
                rejectLinks(root);
                Path backup = root.resolve(".bodriosafio_backups").resolve(id);
                Path stage = root.resolve(".bodriosafio_staging").resolve(id);
                staging.add(stage);
                List<Path> scopes = new ArrayList<>(); scopes.add(root);
                Path worlds = root.resolve("minecraftWorlds");
                if (Files.isDirectory(worlds)) {
                    rejectLinks(worlds);
                    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(worlds)) {
                        for (Path world : stream) if (Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)) {
                            rejectLinks(world);
                            updateReferences(world.resolve("world_behavior_packs.json"), BP, update.behavior.version, root, backup, references);
                            updateReferences(world.resolve("world_resource_packs.json"), RP, update.resource.version, root, backup, references);
                            if (!owned(world.resolve(DIRS[0]), BP).isEmpty() || !owned(world.resolve(DIRS[1]), RP).isEmpty()) scopes.add(world);
                        }
                    }
                }
                for (int scopeIndex = 0; scopeIndex < scopes.size(); scopeIndex++) {
                    Path scope = scopes.get(scopeIndex);
                    for (int type = 0; type < 2; type++) {
                        Pack pack = update.pack(type);
                        Path parent = scope.resolve(DIRS[type]);
                        List<Path> previous = owned(parent, IDS[type]);
                        for (Path p : previous) {
                            if (AddonChannel.compare(new Pack(p).version, pack.version) > 0)
                                throw new IOException("Ya hay un pack de Bodriosafío más nuevo. Se conservó la versión instalada.");
                        }
                        // Only copy into embedded world pack directories that already contain this identity.
                        if (scopeIndex > 0 && previous.isEmpty()) continue;
                        Path destination = parent.resolve("Bodriosafio_" + (type == 0 ? "BP" : "RP"));
                        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !previous.contains(destination))
                            throw new IOException("La carpeta de destino contiene otro pack; no se reemplazó.");
                        boolean same = previous.size() == 1 && previous.get(0).equals(destination) && sameTree(pack.directory, destination);
                        if (same) continue;
                        for (Path p : previous) old.put(new JSONObject().put("source", p.toString())
                                .put("backup", backup.resolve(root.relativize(p)).toString()).put("uuid", IDS[type]));
                        Path staged = stage.resolve("scope-" + scopeIndex).resolve(DIRS[type]);
                        copy(pack.directory, staged);
                        fresh.put(new JSONObject().put("stage", staged.toString()).put("destination", destination.toString()).put("uuid", IDS[type]));
                    }
                }
            }
            if (old.length() == 0 && fresh.length() == 0 && references.length() == 0) return "El addon ya está actualizado.";
            byte[] journalBytes = journal.toString(2).getBytes(StandardCharsets.UTF_8);
            if (journalBytes.length > MAX_JSON) throw new IOException("Hay demasiadas referencias para actualizar de una vez; no se modificaron los packs.");
            write(journalFile, journalBytes);
            step.reached("journal");
            for (int i = 0; i < old.length(); i++) {
                JSONObject op = old.getJSONObject(i);
                move(Paths.get(op.getString("source")), Paths.get(op.getString("backup")));
                step.reached("backup");
            }
            for (int i = 0; i < fresh.length(); i++) {
                JSONObject op = fresh.getJSONObject(i);
                move(Paths.get(op.getString("stage")), Paths.get(op.getString("destination")));
                step.reached("pack");
            }
            for (int i = 0; i < references.length(); i++) {
                JSONObject op = references.getJSONObject(i);
                write(Paths.get(op.getString("path")), op.getString("updated").getBytes(StandardCharsets.UTF_8));
                step.reached("world-reference");
            }
            for (Path root : roots) for (int type = 0; type < 2; type++) {
                List<Path> found = owned(root.resolve(DIRS[type]), IDS[type]);
                if (found.size() != 1 || AddonChannel.compare(new Pack(found.get(0)).version, update.pack(type).version) != 0)
                    throw new IOException("No se pudo verificar la instalación de ambos packs.");
            }
            JSONObject committed = new JSONObject(journal.toString()).put("state", "committed");
            write(journalFile, committed.toString(2).getBytes(StandardCharsets.UTF_8));
            journal.put("state", "committed");
            return "Bodriosafío " + AddonChannel.display(update.behavior.version) + " actualizado. Los mundos conservan sus packs activados.";
        } catch (Exception failure) {
            if (Files.exists(journalFile)) {
                try { rollback(journal, roots); Files.deleteIfExists(journalFile); }
                catch (Exception recovery) { failure.addSuppressed(recovery); throw new IOException("La actualización se interrumpió. Vuelve a abrir Actualizar addon para restaurar la copia anterior.", failure); }
            }
            throw failure;
        } finally {
            // A pending journal owns its staging directories until recovery completes.
            if (!Files.exists(journalFile) || "committed".equals(journal.optString("state"))) for (Path path : staging) {
                try { delete(path); } catch (IOException ignored) { }
            }
        }
    }

    public static synchronized void recover(List<Path> requestedRoots, Path journals) throws Exception {
        List<Path> roots = canonicalRoots(requestedRoots);
        if (!Files.isDirectory(journals)) return;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(journals, "update-*.json")) {
            for (Path path : stream) {
                JSONObject journal = object(path);
                if (journal.getInt("schema") != 1) throw new IOException("Hay una recuperación pendiente de otro formato.");
                if ("pending".equals(journal.getString("state"))) {
                    // Other version profiles can have their own pending update.
                    JSONArray ops = journal.getJSONArray("fresh");
                    boolean relevant = ops.length() > 0 && within(Paths.get(ops.getJSONObject(0).getString("destination")), roots);
                    if (!relevant && journal.getJSONArray("references").length() > 0)
                        relevant = within(Paths.get(journal.getJSONArray("references").getJSONObject(0).getString("path")), roots);
                    if (relevant) { rollback(journal, roots); Files.deleteIfExists(path); }
                }
            }
        }
    }

    private static void rollback(JSONObject journal, List<Path> roots) throws Exception {
        JSONArray fresh = journal.getJSONArray("fresh"), old = journal.getJSONArray("old"), refs = journal.getJSONArray("references");
        for (int i = 0; i < fresh.length(); i++) {
            JSONObject op = fresh.getJSONObject(i);
            Path stage = checked(op.getString("stage"), roots), dest = checked(op.getString("destination"), roots);
            if (!stage.toString().contains(File.separator + ".bodriosafio_staging" + File.separator)) throw new IOException("Ruta de recuperación no válida.");
            if (!Files.exists(stage) && Files.exists(dest)) {
                if (!op.getString("uuid").equals(new Pack(dest).uuid)) throw new IOException("El destino cambió durante la recuperación.");
                delete(dest);
            }
        }
        for (int i = old.length() - 1; i >= 0; i--) {
            JSONObject op = old.getJSONObject(i);
            Path backup = checked(op.getString("backup"), roots), source = checked(op.getString("source"), roots);
            if (!backup.toString().contains(File.separator + ".bodriosafio_backups" + File.separator)) throw new IOException("Ruta de respaldo no válida.");
            if (Files.exists(backup)) {
                if (Files.exists(source)) throw new IOException("La carpeta anterior volvió a aparecer; se conservó el respaldo.");
                move(backup, source);
            }
        }
        for (int i = 0; i < refs.length(); i++) {
            JSONObject op = refs.getJSONObject(i);
            Path target = checked(op.getString("path"), roots), backup = checked(op.getString("backup"), roots);
            if (!backup.toString().contains(File.separator + ".bodriosafio_backups" + File.separator)) throw new IOException("Ruta de respaldo no válida.");
            write(target, Files.readAllBytes(backup));
        }
    }

    private static void updateReferences(Path path, String uuid, int[] version, Path root, Path backup, JSONArray plans) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return;
        rejectLinks(path);
        byte[] bytes = readSmall(path); JSONArray refs = new JSONArray(new String(bytes, StandardCharsets.UTF_8)); boolean changed = false;
        for (int i = 0; i < refs.length(); i++) {
            JSONObject entry = refs.getJSONObject(i);
            if (!uuid.equalsIgnoreCase(entry.optString("pack_id", ""))) continue;
            int comparison = AddonChannel.compare(AddonChannel.version(entry.getJSONArray("version")), version);
            if (comparison > 0) throw new IOException("Un mundo usa una versión más nueva de Bodriosafío. No se redujo su versión.");
            if (comparison < 0) { entry.put("version", new JSONArray(version)); changed = true; }
        }
        if (changed) {
            Path saved = backup.resolve("references").resolve(root.relativize(path));
            Files.createDirectories(saved.getParent()); Files.write(saved, bytes);
            plans.put(new JSONObject().put("path", path.toString()).put("backup", saved.toString()).put("updated", refs.toString(2)));
        }
    }

    private static List<Path> canonicalRoots(List<Path> paths) throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path path : paths) { Path canonical = path.toFile().getCanonicalFile().toPath(); if (!out.contains(canonical)) out.add(canonical); }
        if (out.isEmpty()) throw new IOException("No se encontró el almacenamiento del perfil.");
        return out;
    }
    private static boolean within(Path path, List<Path> roots) throws IOException {
        Path p = path.toFile().getCanonicalFile().toPath();
        for (Path root : roots) if (!p.equals(root) && p.startsWith(root)) return true;
        return false;
    }
    private static Path checked(String path, List<Path> roots) throws IOException {
        Path p = Paths.get(path);
        if (!p.isAbsolute() || !within(p, roots)) throw new IOException("La recuperación intenta salir del perfil.");
        rejectLinks(p); return p;
    }
    private static void rejectLinks(Path path) throws IOException {
        for (Path p = path; p != null; p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("El almacenamiento contiene un enlace; no se modificó.");
    }
    private static JSONObject object(Path path) throws Exception {
        String text = new String(readSmall(path), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return new JSONObject(text);
    }
    private static byte[] readSmall(Path path) throws IOException {
        if (Files.size(path) > MAX_JSON) throw new IOException("Un archivo de configuración supera el tamaño permitido.");
        return Files.readAllBytes(path);
    }
    private static void move(Path from, Path to) throws IOException {
        rejectLinks(from); rejectLinks(to); Files.createDirectories(to.getParent()); Files.move(from, to);
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        rejectLinks(path); Files.createDirectories(path.getParent()); Path temporary = Files.createTempFile(path.getParent(), ".bodriosafio-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void copy(Path from, Path to) throws IOException {
        rejectLinks(from); Files.createDirectories(to);
        try (java.util.stream.Stream<Path> stream = Files.walk(from)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                rejectLinks(p); Path destination = to.resolve(from.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(destination);
                else Files.copy(p, destination);
            }
        }
    }
    private static boolean sameTree(Path a, Path b) throws IOException {
        Set<Path> paths = new HashSet<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(a)) { stream.filter(Files::isRegularFile).forEach(p -> paths.add(a.relativize(p))); }
        Set<Path> actual = new HashSet<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(b)) { stream.filter(Files::isRegularFile).forEach(p -> actual.add(b.relativize(p))); }
        if (!paths.equals(actual)) return false;
        for (Path path : paths) { rejectLinks(b.resolve(path)); if (!Arrays.equals(Files.readAllBytes(a.resolve(path)), Files.readAllBytes(b.resolve(path)))) return false; }
        return true;
    }
    private static void delete(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        rejectLinks(root);
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = new ArrayList<>(); stream.forEach(paths::add); Collections.sort(paths, Comparator.reverseOrder());
            for (Path p : paths) { rejectLinks(p); Files.delete(p); }
        }
    }
}
