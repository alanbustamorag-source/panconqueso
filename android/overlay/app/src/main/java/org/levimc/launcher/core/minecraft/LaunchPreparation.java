// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.core.minecraft;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import org.levimc.launcher.bodriosafio.BodriosafioProfile;
import org.levimc.launcher.core.versions.GameVersion;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keep profile recovery, hashing and addon extraction off the Android UI thread. */
public final class LaunchPreparation {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private LaunchPreparation() {}

    public static boolean isBusy() { return BUSY.get(); }

    public static boolean allowContentChanges(Activity activity) {
        if (!isBusy()) return true;
        Toast.makeText(activity, "Espera a que termine la preparación de Minecraft.", Toast.LENGTH_LONG).show();
        return false;
    }

    public static void start(MinecraftLauncher launcher, Activity activity,
            MinecraftLauncher.LaunchCallback callback, GameVersion version, Intent intent) {
        if (!BUSY.compareAndSet(false, true)) {
            activity.runOnUiThread(() -> launcher.failPreparedLaunch(callback,
                new IllegalStateException("Ya se están preparando los complementos. Espera a que termine.")));
            return;
        }
        Context context = activity.getApplicationContext();
        Thread worker = new Thread(() -> {
            long started = System.nanoTime();
            Exception failure = null;
            try {
                BodriosafioProfile.log(context, "Preparación iniciada en segundo plano.");
                BodriosafioProfile.prepareForLaunch(context, version);
                long elapsed = (System.nanoTime() - started) / 1000000L;
                BodriosafioProfile.log(context, "Preparación terminada en " + elapsed + " ms; abriendo Minecraft.");
            } catch (Exception error) {
                failure = error;
                BodriosafioProfile.log(context, "Preparación fallida: " + error);
            } catch (LinkageError error) {
                failure = new IllegalStateException("No se pudo cargar un componente del launcher.", error);
                BodriosafioProfile.log(context, "Error de carga durante la preparación: " + error);
            }
            final Exception result = failure;
            try {
                activity.runOnUiThread(() -> {
                    try {
                        if (activity.isDestroyed() || activity.isFinishing()) return;
                        if (result != null) {
                            launcher.failPreparedLaunch(callback, result);
                        } else {
                            try { launcher.finishPreparedLaunch(intent, version, callback); }
                            catch (Exception error) { launcher.failPreparedLaunch(callback, error); }
                        }
                    } finally { BUSY.set(false); }
                });
            } catch (RuntimeException dispatchFailure) {
                BUSY.set(false);
                BodriosafioProfile.log(context, "No se pudo volver a la pantalla de inicio: " + dispatchFailure);
            }
        }, "bodriosafio-prepare");
        try { worker.start(); }
        catch (RuntimeException error) {
            BUSY.set(false);
            activity.runOnUiThread(() -> launcher.failPreparedLaunch(callback, error));
        }
    }
}
