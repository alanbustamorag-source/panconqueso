import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.levimc.launcher.bodriosafio.NativeEngineIdentity;

public class NativeEngineIdentityHostTest {
    private static void rejected(File file) throws Exception {
        try { NativeEngineIdentity.requireCompatible(file); }
        catch (IOException expected) { return; }
        throw new AssertionError("An unsupported engine was accepted");
    }
    public static void main(String[] args) throws Exception {
        NativeEngineIdentity.requireCompatible(new File(args[0]));
        rejected(new File(args[1]));
        rejected(null);
        File fake = File.createTempFile("bodriosafio-engine-", ".so");
        try {
            Files.write(fake.toPath(), new byte[] {0x7f, 'E', 'L', 'F'});
            rejected(fake);
        } finally { Files.delete(fake.toPath()); }
        System.out.println("Exact engine accepted; previous, missing and truncated engines rejected");
    }
}
