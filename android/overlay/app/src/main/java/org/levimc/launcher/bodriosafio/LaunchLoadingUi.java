// Bodriosafío: modificación del 23-11-2026 para el nuevo canal y compatibilidad del perfil.
package org.levimc.launcher.bodriosafio;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** A native animated bar while the background profile preparation is running. */
public final class LaunchLoadingUi {
    private static final String TAG = "bodriosafio:launch-loading";

    private LaunchLoadingUi() {}

    public static void attach(Activity activity, TextView status) {
        if (!(status.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) status.getParent();
        if (parent.findViewWithTag(TAG) != null) return;
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setTag(TAG);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(ColorStateList.valueOf(Color.rgb(157, 227, 178)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(25, 39, 32)));
        bar.setPadding(0, 0, 0, 0);
        bar.setContentDescription("Preparando complementos. La primera vez puede tardar.");
        bar.setVisibility(View.GONE);
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(6 * density));
        params.bottomMargin = Math.round(18 * density);
        parent.addView(bar, parent.indexOfChild(status) + 1, params);
    }

    public static void show(Activity activity) { setVisible(activity, true); }

    public static void hide(Activity activity) { setVisible(activity, false); }

    private static void setVisible(Activity activity, boolean loading) {
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) return;
            View bar = activity.getWindow().getDecorView().findViewWithTag(TAG);
            if (bar != null) bar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });
    }
}
