import org.levimc.launcher.bodriosafio.ProfileRules;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ProfileRulesHostTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(ProfileRules.compatible("26.60"), "year version");
        check(ProfileRules.compatible("1.26.60"), "internal version");
        check(ProfileRules.compatible("1.26.60.1"), "servicing version");
        check(ProfileRules.compatible("26.62"), "Android stable hotfix");
        check(ProfileRules.compatible("1.26.62.1"), "Android internal hotfix");
        check(ProfileRules.compatible("26.63"), "verified stable release");
        check(!ProfileRules.compatible("26.70.26-preview"), "reject preview");
        check(ProfileRules.compatible("26.66"), "new Android hotfix");
        check(ProfileRules.nativeCompatible("1.26.66.1"), "validated native build");
        check(ProfileRules.nativeCompatible("1.26.66.01"), "native release version padding");
        check(ProfileRules.nativeCompatible("26.66"), "native displayed release");
        check(!ProfileRules.nativeCompatible("1.26.66.2"), "unverified servicing build");
        check(!ProfileRules.nativeCompatible("1.26.60.4"), "previous profile needs its previous module");
        check(!ProfileRules.nativeCompatible("1.26.62.1"), "no native offsets on new hotfix");
        check(!ProfileRules.nativeCompatible("1.26.60.5"), "reject different Android native build");
        check(!ProfileRules.nativeCompatible(null), "unknown native build");
        ProfileRules.requireNativeVersion("1.26.66.1");
        check(true, "known native build may prepare");
        for (String unsupported : new String[] {"26.62", "1.26.63.1", "1.26.60.5", null}) {
            boolean nativeRejected = false;
            try { ProfileRules.requireNativeVersion(unsupported); }
            catch (java.io.IOException expected) { nativeRejected = true; }
            check(nativeRejected, "unsupported build must not fall back to addon mode");
        }
        check(!ProfileRules.compatible("26.600"), "reject similar but different release");
        check(!ProfileRules.compatible("26.61"), "reject next release");
        check(!ProfileRules.compatible("1.26.60.25-beta"), "reject beta");
        check(!ProfileRules.compatible(null), "reject unknown version");
        check(ProfileRules.displayVersion("1.26.60.1").equals("26.60.1"), "actual display version");
        check(ProfileRules.displayVersion("1.26.61").equals("26.61"), "display incompatible version truthfully");
        check(ProfileRules.displayVersion("1.21.130").equals("1.21.130"), "retain old numbering");
        check(ProfileRules.displayVersion(null).equals("Sin detectar"), "unknown display");
        check(ProfileRules.sha256("abc".getBytes(StandardCharsets.UTF_8)).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "SHA-256 implementation");
        String defaults = ProfileRules.configureNether("");
        check(defaults.contains("[Nether]\nmin=0\nmax=256"), "default profile");
        check(defaults.contains("min=-64\nmax=320"), "default Overworld");
        check(defaults.equals(ProfileRules.configureNether(defaults)), "default idempotence");
        String original = "# notes\nhook_version=2\n[Overworld]\nmin=-128\nmax=512\n[Nether]\nmin=-16\nmax=128 # ceiling\n[Extra]\nmin=0\nmax=1024\n";
        String updated = ProfileRules.configureNether(original);
        check(updated.contains("[Overworld]\nmin=-128\nmax=512"), "preserve other dimensions");
        check(updated.contains("[Extra]\nmin=0\nmax=1024"), "preserve extra dimension");
        check(updated.contains("hook_version=2"), "preserve hook choice");
        check(updated.contains("max=256 # ceiling"), "preserve comment");
        check(updated.equals(ProfileRules.configureNether(updated)), "custom idempotence");
        check(ProfileRules.configureNether("[Nether]\nmax=128\n").contains("min=0"), "insert missing minimum");
        check(ProfileRules.configureNether("[Nether]\nmin=0\n").contains("max=256"), "insert missing maximum");
        String repeated = ProfileRules.configureNether("[Nether]\nmax=128\nmax=144\n[Nether]\nmax=128\n");
        check(!repeated.contains("max=128") && !repeated.contains("max=144"), "update all duplicate settings");
        check(ProfileRules.configureNether("[Overworld]\nmax=512").contains("[Nether]\nmin=0\nmax=256\n"), "append absent section");
        check(ProfileRules.configureNether("[Nether]\r\nmax=128\r\n").contains("max=256\r\n"), "preserve CRLF");
        check(!ProfileRules.configureNether("\uFEFF[Nether]\nmax=128").startsWith("\uFEFF"), "remove unsupported BOM");
        boolean rejected = false;
        try { ProfileRules.configureNether("[Nether]\ntext without equals\n"); } catch (Exception expected) { rejected=true; }
        check(rejected, "reject malformed text");
        rejected=false; try { ProfileRules.configureNether("[Nether\nmax=128"); } catch (Exception expected) { rejected=true; }
        check(rejected, "reject malformed section");
        rejected=false; try { ProfileRules.configureNether("a\0b"); } catch (Exception expected) { rejected=true; }
        check(rejected, "reject binary input");
        File dir=Files.createTempDirectory("bodriosafio-config-test").toFile();
        try {
            File config=new File(dir,"BuildLimitChanger/config.ini");
            check(ProfileRules.prepareConfig(config), "write first profile");
            check(!ProfileRules.prepareConfig(config), "do not rewrite matching profile");
            Files.write(config.toPath(), original.getBytes(StandardCharsets.UTF_8));
            check(ProfileRules.prepareConfig(config), "update existing profile");
            File[] backups=config.getParentFile().listFiles((folder,name)->name.endsWith(".bak"));
            check(backups!=null && backups.length==1, "one backup before replacement");
            check(new String(Files.readAllBytes(backups[0].toPath()),StandardCharsets.UTF_8).equals(original), "backup preserves exact source");
            check(!ProfileRules.prepareConfig(config), "repeat does not modify profile");
            check(config.getParentFile().listFiles((folder,name)->name.endsWith(".bak")).length==1, "no redundant backups");
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(dir.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(Exception ignored){}});
            }
        }
        System.out.println(checks + " Android profile/version checks passed (host JVM, not Minecraft)");
    }
}
