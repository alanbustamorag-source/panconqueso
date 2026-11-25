package org.levimc.launcher.bodriosafio;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Shared download display: a bar and a percentage, without file names or logs. */
public final class DownloadProgressUi {
    private final Activity owner;
    private final LinearLayout container;
    private final ProgressBar bar;
    private final TextView percentage;
    private int last = -2;

    public DownloadProgressUi(Activity owner, LinearLayout parent, int index) {
        this.owner = owner;
        float density = owner.getResources().getDisplayMetrics().density;
        container = new LinearLayout(owner);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setVisibility(View.GONE);
        percentage = new TextView(owner);
        percentage.setTextColor(Color.rgb(234, 243, 237));
        percentage.setTextSize(15);
        percentage.setGravity(Gravity.END);
        container.addView(percentage, new LinearLayout.LayoutParams(-1, -2));
        bar = new ProgressBar(owner, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        ColorStateList accent = ColorStateList.valueOf(Color.rgb(157, 227, 178));
        bar.setProgressTintList(accent);
        bar.setIndeterminateTintList(accent);
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(49, 69, 58)));
        LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(-1, Math.round(8 * density));
        track.topMargin = Math.round(6 * density);
        container.addView(bar, track);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = Math.round(10 * density);
        layout.bottomMargin = Math.round(16 * density);
        parent.addView(container, index, layout);
    }

    public void begin() { display(0); }

    /** Hold 100% until the caller has also verified and saved the file. */
    public void update(long received, long total) {
        int value = total > 0 ? (int) Math.min(99, Math.max(0, received) * 100 / total) : -1;
        display(value);
    }

    public void complete() { display(100); }

    public void hide() {
        owner.runOnUiThread(() -> {
            if (owner.isDestroyed() || owner.isFinishing()) return;
            last = -2;
            container.setVisibility(View.GONE);
        });
    }

    private void display(int value) {
        owner.runOnUiThread(() -> {
            if (owner.isDestroyed() || owner.isFinishing() || last == value) return;
            last = value;
            container.setVisibility(View.VISIBLE);
            bar.setIndeterminate(value < 0);
            if (value >= 0) bar.setProgress(value, true);
            percentage.setText(value < 0 ? "…" : value + "%");
            bar.setContentDescription(value < 0 ? "Cargando" : value + "%");
        });
    }
}
