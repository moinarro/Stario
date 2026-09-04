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

package com.stario.launcher.ui.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;

import java.util.List;

/**
 * A single grid tile that hosts several real, independently bound app
 * widgets as horizontally-swipeable pages instead of just one - a "widget
 * stack" / carousel, added through the same picker as any other widget
 * (see WidgetConfigurator's pinned "Widget stack" entry).
 * <p>
 * Ownership stays with the caller (WidgetsDialog): this view only ever
 * asks for child views through {@link Callback#createChildView(int)} and
 * reports additions/removals back - it never touches AppWidgetHost/
 * AppWidgetManager itself, so the same allocate/bind/configure flow used
 * for top-level widgets can be reused unchanged for stack children.
 */
@SuppressLint("ViewConstructor")
public class WidgetStackView extends FrameLayout {
    private final List<Integer> children;
    private final Callback callback;
    private final ChildAdapter adapter;
    private final TextView pageIndicator;
    private final RecyclerView recycler;

    public WidgetStackView(Context context, List<Integer> children, Callback callback) {
        super(context);

        this.children = children;
        this.callback = callback;

        // WidgetContainer (this view's eventual parent) is a RelativeLayout,
        // so its onMeasure() casts the child's LayoutParams to
        // RelativeLayout.LayoutParams - match that up front the same way
        // WidgetHost.onCreateView() does for a plain AppWidgetHostView,
        // rather than leaving RelativeLayout to generate a WRAP_CONTENT
        // default that would shrink this view to nothing.
        setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));

        View root = LayoutInflater.from(context)
                .inflate(R.layout.widget_stack_container, this, true);

        pageIndicator = root.findViewById(R.id.page_indicator);
        recycler = root.findViewById(R.id.recycler);

        LinearLayoutManager manager = new LinearLayoutManager(context,
                LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(manager);
        recycler.setItemAnimator(null);

        // The stack lives inside the widgets sheet, which is itself
        // dismissed by a horizontal swipe (RightSheetBehavior). That sheet
        // decides whether to treat a horizontal drag as "close me" from the
        // raw touch stream, before this recycler ever sees the event - so
        // without this, swiping to the previous/next widget at the very
        // start of the gesture gets stolen and closes the sheet instead of
        // paging the stack. Claiming the touch stream up front lets the
        // recycler's own (already boundary-aware) nested-scroll dispatch
        // negotiate with the sheet instead, which correctly hands off to
        // the sheet's close gesture only once there is no more previous/next
        // widget to scroll to.
        recycler.setNestedScrollingEnabled(true);
        recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        ViewParent parent = view.getParent();

                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ViewParent releasedParent = view.getParent();

                        if (releasedParent != null) {
                            releasedParent.requestDisallowInterceptTouchEvent(false);
                        }
                        break;
                }

                return false;
            }
        });

        new PagerSnapHelper().attachToRecyclerView(recycler);

        adapter = new ChildAdapter();
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                updatePageIndicator(manager);
            }
        });

        setOnHeaderLongClickListener(null);
        updatePageIndicator(manager);
    }

    public void setOnHeaderLongClickListener(OnLongClickListener listener) {
        View header = findViewById(R.id.header);

        header.setLongClickable(listener != null);
        header.setOnLongClickListener(listener);
    }

    /**
     * Call after the children list changes (a child was added or removed)
     * to refresh the carousel and the page indicator.
     */
    public void notifyChildrenChanged() {
        adapter.notifyDataSetChanged();
        updatePageIndicator((LinearLayoutManager) recycler.getLayoutManager());
    }

    private void updatePageIndicator(LinearLayoutManager manager) {
        if (pageIndicator == null || manager == null || children.isEmpty()) {
            if (pageIndicator != null) {
                pageIndicator.setText("");
            }

            return;
        }

        int position = manager.findFirstVisibleItemPosition();

        if (position < 0) {
            return;
        }

        int page = Math.min(position, children.size() - 1) + 1;

        pageIndicator.setText(getContext().getString(R.string.widget_stack_page_count,
                page, children.size()));
    }

    private class ChildAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ADD = -1;

        @Override
        public int getItemViewType(int position) {
            return position < children.size() ? children.get(position) : TYPE_ADD;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ADD) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.widget_stack_add_page, parent, false);

                view.setOnClickListener(v -> {
                    if (callback != null) {
                        callback.onAddRequested(WidgetStackView.this);
                    }
                });

                return new RecyclerView.ViewHolder(view) {
                };
            }

            int appWidgetId = viewType;

            FrameLayout wrapper = new FrameLayout(parent.getContext());
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (callback != null) {
                View host = callback.createChildView(appWidgetId);

                if (host != null) {
                    if (host.getParent() instanceof ViewGroup) {
                        ((ViewGroup) host.getParent()).removeView(host);
                    }

                    host.setLayoutParams(new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    host.setOnLongClickListener(v -> {
                        callback.onRemoveRequested(WidgetStackView.this, appWidgetId);

                        return true;
                    });

                    wrapper.addView(host);
                }
            }

            return new RecyclerView.ViewHolder(wrapper) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            // Nothing to bind: the view type already identifies exactly which
            // child (or the trailing add page) this holder is for, so its
            // content is fully built in onCreateViewHolder(). This trades a
            // (harmless, since stacks hold a handful of children at most)
            // larger RecycledViewPool for the guarantee that a live,
            // stateful AppWidgetHostView never gets silently rebound to a
            // different widget.
        }

        @Override
        public int getItemCount() {
            return children.size() + 1;
        }
    }

    public interface Callback {
        /**
         * Build (or reuse) the real, already-bound AppWidgetHostView for
         * this child. May return null if the widget can no longer be
         * resolved (e.g. its provider was uninstalled).
         */
        View createChildView(int appWidgetId);

        void onAddRequested(WidgetStackView stack);

        void onRemoveRequested(WidgetStackView stack, int appWidgetId);
    }
}
