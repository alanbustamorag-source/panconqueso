package org.levimc.launcher.bodriosafio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Verify the actual library before any native mod can patch the game process. */
public final class NativeEngineIdentity {
    public static final String SHA256 = "3c24bf3a20471be41449f1e5cd9354c657995468e412d1631d9828b3f3824892";
    private NativeEngineIdentity() {}

    public static void requireCompatible(File engine) throws IOException {
        if (engine == null || !engine.isFile())
            throw new IOException("No se encontró el motor de Minecraft para comprobar los complementos.");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[256 * 1024];
            try (FileInputStream input = new FileInputStream(engine)) {
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            byte[] actual = digest.digest();
            byte[] expected = new byte[32];
            for (int i = 0; i < expected.length; i++)
                expected[i] = (byte) Integer.parseInt(SHA256.substring(i * 2, i * 2 + 2), 16);
            if (!MessageDigest.isEqual(actual, expected))
                throw new IOException("Esta compilación de Minecraft no coincide con el motor 26.66 verificado. Los complementos necesitan una adaptación para esta copia.");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("No se pudo comprobar el motor de Minecraft.", unavailable);
        }
    }
}
