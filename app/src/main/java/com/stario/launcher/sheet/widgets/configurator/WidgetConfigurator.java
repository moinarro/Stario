/*
 * Copyright (C) 2025 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.stario.launcher.sheet.widgets.configurator;

import android.appwidget.AppWidgetProviderInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.stario.launcher.R;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.sheet.widgets.WidgetSize;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;
import com.stario.launcher.ui.Measurements;
import com.stario.launcher.ui.recyclers.DividerItemDecorator;

public class WidgetConfigurator extends ActionDialog {
    private static final String TAG = "WidgetConfigurator";
    private final Request requestListener;
    private NestedScrollView scroller;
    private WidgetListAdapter adapter;

    public WidgetConfigurator(@NonNull ThemedActivity activity, @NonNull Request requestListener) {
        super(activity);

        this.requestListener = requestListener;
    }

    @NonNull
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View contentView = inflater.inflate(R.layout.widget_picker, null);

        scroller = contentView.findViewById(R.id.scroller);
        scroller.setClipToOutline(true);

        RecyclerView recycler = contentView.findViewById(R.id.container_widgets);

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false);

        adapter = new WidgetListAdapter(activity, recycler, requestListener);

        recycler.setAdapter(adapter);
        recycler.setLayoutManager(layoutManager);
        recycler.addItemDecoration(new DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL));

        setupStackEntry(contentView);

        return contentView;
    }

    /**
     * A pinned, always-visible entry above the per-app widget list that
     * creates an empty widget stack instead of binding a specific app's
     * widget. Reuses the same widget_picker_preview layout (and its S/M/L/XL
     * size buttons) as a regular entry so the interaction is identical.
     */
    private void setupStackEntry(View contentView) {
        FrameLayout container = contentView.findViewById(R.id.stack_entry);
        View entry = LayoutInflater.from(activity)
                .inflate(R.layout.widget_picker_preview, container, false);

        ConstraintLayout preview = entry.findViewById(R.id.preview);
        TextView label = entry.findViewById(R.id.label);
        View options = entry.findViewById(R.id.options);

        label.setText(R.string.widget_stack);

        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_add_page));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Measurements.dpToPx(96));
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

        preview.addView(icon, params);

        entry.setOnClickListener(v -> {
            Vibrations.getInstance().vibrate();

            if (options.getVisibility() != View.VISIBLE) {
                preview.animate().alpha(0.3f);
                options.setVisibility(View.VISIBLE);

                entry.findViewById(R.id.small).setOnClickListener(sv ->
                        requestListener.requestStackAddition(WidgetSize.SMALL));
                entry.findViewById(R.id.medium).setOnClickListener(sv ->
                        requestListener.requestStackAddition(WidgetSize.MEDIUM));
                entry.findViewById(R.id.large).setOnClickListener(sv ->
                        requestListener.requestStackAddition(WidgetSize.LARGE));
                entry.findViewById(R.id.xlarge).setOnClickListener(sv ->
                        requestListener.requestStackAddition(WidgetSize.XLARGE));
            } else {
                preview.animate().alpha(1f);
                options.setVisibility(View.INVISIBLE);
            }
        });

        container.removeAllViews();
        container.addView(entry);
    }

    @Override
    protected boolean blurBehind() {
        return true;
    }

    @Override
    public void show() {
        super.show();

        scroller.scrollTo(0, 0);
        adapter.update();
    }

    @Override
    protected int getDesiredInitialState() {
        if (!Measurements.isLandscape()) {
            return BottomSheetBehavior.STATE_HALF_EXPANDED;
        }

        return BottomSheetBehavior.STATE_EXPANDED;
    }

    public interface Request {
        void requestAddition(AppWidgetProviderInfo info, WidgetSize size);

        /**
         * Requested a new, empty widget stack (a horizontally-scrolling
         * carousel of several real app widgets in a single tile) rather
         * than a specific app's widget.
         */
        void requestStackAddition(WidgetSize size);
    }
}
