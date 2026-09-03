/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.stario.launcher.activities.settings.dialogs.gestures;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.stario.launcher.R;
import com.stario.launcher.apps.ProfileApplicationManager;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;
import com.stario.launcher.ui.icons.AdaptiveIconView;
import com.stario.launcher.ui.recyclers.autogrid.AutoGridLayoutManager;
import com.stario.launcher.ui.utils.LayoutSizeObserver;

public class GestureAppPickerDialog extends ActionDialog {
    private final OnApplicationPickedListener listener;

    public GestureAppPickerDialog(@NonNull ThemedActivity activity, OnApplicationPickedListener listener) {
        super(activity);

        this.listener = listener;
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_gesture_app_picker, null);
        RecyclerView recycler = root.findViewById(R.id.recycler);

        root.findViewById(R.id.none_container).setOnClickListener(view -> {
            Vibrations.getInstance().vibrate();

            if (listener != null) {
                listener.onPicked(null);
            }

            dismiss();
        });

        ProfileApplicationManager applicationManager =
                ProfileManager.getInstance().getProfile(ProfileManager.getOwner());

        AutoGridLayoutManager manager = new AutoGridLayoutManager(activity, 1);
        GestureAppPickerAdapter adapter = new GestureAppPickerAdapter(activity, applicationManager,
                packageName -> {
                    if (listener != null) {
                        listener.onPicked(packageName);
                    }

                    dismiss();
                });

        LayoutSizeObserver.attach(root, LayoutSizeObserver.WIDTH, new LayoutSizeObserver.OnChange() {
            @Override
            public void onChange(View view, int watchFlags, Rect rect) {
                int columns = MathUtils.clamp(rect.width() /
                                (AdaptiveIconView.getMaxIconSize() + 40),
                        3, 6);

                manager.setSpanCount(columns);
            }
        });

        recycler.setItemAnimator(null);
        recycler.setLayoutManager(manager);
        recycler.setAdapter(adapter);

        return root;
    }

    @Override
    protected boolean blurBehind() {
        return true;
    }

    @Override
    protected int getDesiredInitialState() {
        return BottomSheetBehavior.STATE_EXPANDED;
    }

    public interface OnApplicationPickedListener {
        void onPicked(String packageName);
    }
}
