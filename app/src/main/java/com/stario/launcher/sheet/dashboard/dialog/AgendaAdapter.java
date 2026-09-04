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

package com.stario.launcher.sheet.dashboard.dialog;

import android.content.ContentUris;
import android.content.Intent;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;
import com.stario.launcher.themes.ThemedActivity;

import java.util.List;

/**
 * Backs the Utilidades page's "Agenda de hoy" - one row per
 * TodayAgenda.Event, most imminent first. Tapping a row opens the system
 * calendar app to that event, the same navigation Calendar (the Glance
 * date chip) already uses for "today" in general.
 */
class AgendaAdapter extends RecyclerView.Adapter<AgendaAdapter.EventViewHolder> {
    private final ThemedActivity activity;
    private final LayoutInflater inflater;
    private List<TodayAgenda.Event> events;

    AgendaAdapter(ThemedActivity activity, List<TodayAgenda.Event> events) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.events = events;
    }

    void setEvents(List<TodayAgenda.Event> events) {
        this.events = events;

        notifyDataSetChanged();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView title;

        EventViewHolder(View itemView) {
            super(itemView);

            time = itemView.findViewById(R.id.time);
            title = itemView.findViewById(R.id.title);
        }
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dashboard_agenda_item, parent, false);

        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        TodayAgenda.Event event = events.get(position);

        holder.time.setText(event.allDay ? "" :
                DateFormat.getTimeFormat(activity).format(event.begin));
        holder.title.setText(event.title);

        holder.itemView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id));

                activity.startActivity(intent);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }
}
