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

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileApplicationManager;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.recyclers.async.InflationType;

import java.util.function.Supplier;

class GestureAppPickerAdapter extends RecyclerApplicationAdapter {
    private final ProfileApplicationManager applicationManager;
    private final OnApplicationPickedListener listener;

    GestureAppPickerAdapter(ThemedActivity activity, ProfileApplicationManager applicationManager,
                            OnApplicationPickedListener listener) {
        super(activity, true, InflationType.ASYNC);

        this.applicationManager = applicationManager;
        this.listener = listener;

        setHasStableIds(true);
    }

    public class PickerViewHolder extends ApplicationViewHolder {
        public PickerViewHolder(int viewType) {
            super(viewType);
        }

        @Override
        public View.OnClickListener getOnClickListener() {
            return view -> {
                Vibrations.getInstance().vibrate();

                int index = getBindingAdapterPosition();
                if (index == RecyclerView.NO_POSITION) {
                    return;
                }

                LauncherApplication application = getApplication(index);

                if (application != LauncherApplication.FALLBACK_APP && listener != null) {
                    listener.onPicked(application.getInfo().packageName);
                }
            };
        }

        @Override
        public View.OnLongClickListener getOnLongClickListener() {
            return null;
        }
    }

    @Override
    protected LauncherApplication getApplication(int index) {
        return applicationManager != null ?
                applicationManager.get(index) : LauncherApplication.FALLBACK_APP;
    }

    @Override
    public int getTotalItemCount() {
        return applicationManager != null ? applicationManager.getSize() : 0;
    }

    @Override
    protected Supplier<ApplicationViewHolder> getHolderSupplier(int viewType) {
        return () -> new PickerViewHolder(viewType);
    }

    @Override
    protected boolean allowApplicationStateEditing() {
        return false;
    }

    public interface OnApplicationPickedListener {
        void onPicked(String packageName);
    }
}
