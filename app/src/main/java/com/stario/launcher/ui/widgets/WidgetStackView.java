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
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.stario.launcher.R;
import com.stario.launcher.ui.Measurements;

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
    // Above this many children, dots would either overflow the header or
    // become too small to tap reliably - fall back to the plain "x / y"
    // text instead of trying to cram that many dots in.
    private static final int MAX_DOTS = 8;

    private final List<Integer> children;
    private final Callback callback;
    private final ChildAdapter adapter;
    private final TextView nameView;
    private final TextView pageIndicator;
    private final LinearLayout dots;
    private final RecyclerView recycler;
    private final LinearLayoutManager layoutManager;
    private final PagerSnapHelper snapHelper;

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

        nameView = root.findViewById(R.id.name);
        pageIndicator = root.findViewById(R.id.page_indicator);
        dots = root.findViewById(R.id.dots);
        recycler = root.findViewById(R.id.recycler);

        layoutManager = new LinearLayoutManager(context,
                LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(layoutManager);
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

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        adapter = new ChildAdapter();
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                updatePageIndicator();
            }
        });

        setOnHeaderLongClickListener(null);
        updatePageIndicator();
    }

    public void setOnHeaderLongClickListener(OnLongClickListener listener) {
        View header = findViewById(R.id.header);

        header.setLongClickable(listener != null);
        header.setOnLongClickListener(listener);
    }

    /**
     * Shows the given label in the header, or hides it entirely when null
     * or blank so the plain dot/page indicator gets the header's full
     * width, matching a stack that was never renamed.
     */
    public void setName(@Nullable String name) {
        if (TextUtils.isEmpty(name)) {
            nameView.setVisibility(GONE);
            nameView.setText(null);
        } else {
            nameView.setText(name);
            nameView.setVisibility(VISIBLE);
        }
    }

    /**
     * Number of real widgets currently in the stack (the trailing "add"
     * page doesn't count).
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Snap directly to the page at {@code index} (clamped into range),
     * animating the same way a manual swipe would settle. Used by both the
     * tappable page dots and a two-finger jump-to-first/last gesture.
     */
    public void scrollToPage(int index) {
        if (children.isEmpty()) {
            return;
        }

        int target = Math.max(0, Math.min(index, children.size() - 1));

        recycler.smoothScrollToPosition(target);
    }

    /**
     * Call after the children list changes (a child was added or removed)
     * to refresh the carousel and the page indicator.
     */
    public void notifyChildrenChanged() {
        adapter.notifyDataSetChanged();
        updatePageIndicator();
    }

    private void updatePageIndicator() {
        if (children.isEmpty()) {
            pageIndicator.setVisibility(GONE);
            dots.setVisibility(GONE);

            return;
        }

        int position = layoutManager.findFirstVisibleItemPosition();

        if (position < 0) {
            return;
        }

        int page = Math.min(position, children.size() - 1);

        if (children.size() > MAX_DOTS) {
            dots.setVisibility(GONE);
            pageIndicator.setVisibility(VISIBLE);

            pageIndicator.setText(getContext().getString(R.string.widget_stack_page_count,
                    page + 1, children.size()));
        } else {
            pageIndicator.setVisibility(GONE);

            updateDots(page);
        }
    }

    private void updateDots(int selected) {
        if (dots.getChildCount() != children.size()) {
            dots.removeAllViews();

            int size = Measurements.dpToPx(6);
            int margin = Measurements.dpToPx(2);

            for (int index = 0; index < children.size(); index++) {
                View dot = new View(getContext());

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                params.setMargins(margin, 0, margin, 0);
                dot.setLayoutParams(params);

                int page = index;
                dot.setOnClickListener(v -> scrollToPage(page));

                dots.addView(dot);
            }
        }

        for (int index = 0; index < dots.getChildCount(); index++) {
            View dot = dots.getChildAt(index);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(MaterialColors.getColor(dot,
                    com.google.android.material.R.attr.colorOnSurface));

            dot.setBackground(background);
            dot.setAlpha(index == selected ? 0.9f : 0.3f);
        }

        dots.setVisibility(VISIBLE);
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
