import org.levimc.launcher.bodriosafio.update.LauncherChannel;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

/** Actual APK bytes and malformed public feeds, independent of Android UI. */
public final class LauncherUpdaterHostTest {
    static int checks;
    interface Test { void run() throws Exception; }
    static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); checks++; }
    static void rejects(Test test, String name) throws Exception {
        try { test.run(); throw new AssertionError(name); }
        catch (AssertionError error) { throw error; } catch (Exception expected) { checks++; }
    }
    static final OutputStream DISCARD = new OutputStream() { public void write(int value) {} public void write(byte[] value,int offset,int length) {} };
    static String digest(byte[] data) throws Exception { return LauncherChannel.hex(MessageDigest.getInstance("SHA-256").digest(data)); }
    static JSONObject fixture(byte[] data) throws Exception {
        return new JSONObject().put("schema",1).put("project","bodriosafio").put("kind","launcher").put("platform","android")
            .put("package",LauncherChannel.PACKAGE).put("version","0.1.5").put("version_code",10006).put("min_android",28)
            .put("size",data.length).put("sha256",digest(data)).put("notes","Prueba")
            .put("url","https://github.com/alanbustamorag-source/panconqueso/releases/download/android-0.1.5-r2/Bodriosafio_Launcher_Android_0_1_5.apk");
    }
    static void bad(byte[] bytes, String field, Object value) throws Exception {
        JSONObject json=fixture(bytes).put(field,value);
        rejects(()->new LauncherChannel(json.toString()),"reject "+field+"="+value);
    }
    public static void main(String[] args) throws Exception {
        byte[] apk=Files.readAllBytes(Path.of(args[0]));
        LauncherChannel channel=new LauncherChannel(fixture(apk).toString());
        check(channel.newerThan(10005),"same display version, newer revision");
        check(!channel.newerThan(10006),"current revision never loops");
        check(!channel.newerThan(10007),"downgrade never offered");
        check(channel.version.equals("0.1.5"),"visible version retained");
        channel.verify(Path.of(args[0]).toFile());checks++;
        long[] progress={0};
        channel.copyVerified(new ByteArrayInputStream(apk),DISCARD,(received,total)->{
            if (received<progress[0] || received>total) throw new AssertionError("invalid progress");
            progress[0]=received;
        });check(progress[0]==apk.length,"real APK streaming complete");
        byte[] corrupt=apk.clone();corrupt[corrupt.length/2]^=1;
        rejects(()->channel.copyVerified(new ByteArrayInputStream(corrupt),DISCARD,null),"corrupt APK");
        rejects(()->channel.copyVerified(new ByteArrayInputStream(apk,0,apk.length-1),DISCARD,null),"truncated APK");
        byte[] tooBig=Arrays.copyOf(apk,apk.length+1);
        rejects(()->channel.copyVerified(new ByteArrayInputStream(tooBig),DISCARD,null),"oversized download");
        bad(apk,"schema",2);
        rejects(()->new LauncherChannel(fixture(apk).toString().replace("\"schema\":1","\"schema\":1.0")),"fractional JSON schema token");
        bad(apk,"project","other");bad(apk,"kind","addon");
        bad(apk,"platform","windows");bad(apk,"package","com.mojang.minecraftpe");
        bad(apk,"version","0.1.5-beta");bad(apk,"version_code",10006.5);bad(apk,"version_code","10006");
        bad(apk,"version_code",0);bad(apk,"version_code",2147483648L);bad(apk,"version_code",-1);
        bad(apk,"min_android",27);bad(apk,"min_android",1000);bad(apk,"min_android",28.5);
        bad(apk,"size",0);bad(apk,"size",LauncherChannel.MAX_APK+1);bad(apk,"size",1.5);
        bad(apk,"sha256","abc");bad(apk,"notes","x".repeat(4001));
        for(String url:new String[]{
            "http://github.com/alanbustamorag-source/panconqueso/releases/download/v1/a.apk",
            "https://github.com/other/repo/releases/download/v1/a.apk",
            "https://github.com.evil.test/alanbustamorag-source/panconqueso/releases/download/v1/a.apk",
            "https://github.com/alanbustamorag-source/panconqueso-evil/releases/download/v1/a.apk",
            "https://github.com/alanbustamorag-source/panconqueso/releases/download/v1/../a.apk",
            "https://github.com/alanbustamorag-source/panconqueso/releases/download/v1/%2e%2e/a.apk",
            "https://github.com/alanbustamorag-source/panconqueso/releases/download/v1/a.apk?other=1",
            "https://user@github.com/alanbustamorag-source/panconqueso/releases/download/v1/a.apk",
            "https://github.com:444/alanbustamorag-source/panconqueso/releases/download/v1/a.apk",
            "https://github.com/alanbustamorag-source/panconqueso/releases/download/v1/a.apk#part",
            "https://raw.githubusercontent.com/other/repo/main/a.apk",
            "https://github.com/alanbustamorag-source/panconqueso/releases/download/v1/a.zip"
        }) rejects(()->LauncherChannel.validateDownloadUrl(url),"foreign/malformed URL");
        LauncherChannel.validateDownloadUrl("https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/a.apk");checks++;
        LauncherChannel.validateRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/1/a?sig=example");checks++;
        LauncherChannel.validateRedirect("https://objects.githubusercontent.com/github-production-release-asset/1/a?sig=example");checks++;
        rejects(()->LauncherChannel.validateRedirect("http://release-assets.githubusercontent.com/a"),"HTTPS downgrade");
        rejects(()->LauncherChannel.validateRedirect("https://evil.test/a.apk"),"foreign redirect");
        rejects(()->LauncherChannel.validateRedirect("https://release-assets.githubusercontent.com.evil.test/a.apk"),"fake CDN");
        JSONObject noDigest=fixture(apk);noDigest.remove("sha256");rejects(()->new LauncherChannel(noDigest.toString()),"missing hash");
        check(new LauncherChannel(channel.asJson()).versionCode==10006,"validated cached feed");
        System.out.println("PASS: "+checks+" launcher updater checks using real APK bytes");
    }
}
