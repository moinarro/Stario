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

package com.stario.launcher.sheet.widgets.dialog;

import android.app.Activity;
import android.app.ActivityOptions;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.stario.launcher.R;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.sheet.SheetDialogFragment;
import com.stario.launcher.sheet.SheetType;
import com.stario.launcher.sheet.widgets.Widget;
import com.stario.launcher.sheet.widgets.WidgetSize;
import com.stario.launcher.sheet.widgets.WidgetStack;
import com.stario.launcher.sheet.widgets.configurator.WidgetConfigurator;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.Measurements;
import com.stario.launcher.ui.common.FadingEdgeLayout;
import com.stario.launcher.ui.popup.PopupMenu;
import com.stario.launcher.ui.utils.animation.Animation;
import com.stario.launcher.ui.widgets.WidgetContainer;
import com.stario.launcher.ui.widgets.WidgetGrid;
import com.stario.launcher.ui.widgets.WidgetHost;
import com.stario.launcher.ui.widgets.WidgetScroller;
import com.stario.launcher.ui.widgets.WidgetStackView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class WidgetsDialog extends SheetDialogFragment {
    private static final String TAG = "WidgetsDialog";
    private static final int HOST_ID = 219672;
    private static final int MAX_COUNT = 15;
    private static final int CONFIGURATION_CODE = 3264614;
    private static int columnSize = 0;

    private ActivityResultLauncher<Intent> bindWidgetRequest;
    private ActivityResultLauncher<Intent> stackBindWidgetRequest;
    private WidgetConfigurator configurator;
    private WidgetConfigurator stackConfigurator;
    private boolean isConfiguratorVisible;
    private boolean isStackConfiguratorVisible;
    private SharedPreferences widgetStore;
    private SharedPreferences widgetStackStore;
    private WidgetSize pendingWidgetSize;
    // Target of an in-flight "add a widget to this stack" flow. Only one such
    // flow can be in progress at a time (the picker is modal), so plain
    // fields - rather than something keyed per-stack - are enough.
    private Widget pendingStackWidget;
    private WidgetStack pendingStack;
    private WidgetStackView pendingStackView;
    private AppWidgetManager manager;
    private WidgetScroller scroller;
    private View addWidgetContainer;
    private ThemedActivity activity;
    private ViewGroup placeholder;
    private LinearLayout content;
    private WidgetHost host;
    private WidgetGrid grid;

    public WidgetsDialog() {
        super();

        this.isConfiguratorVisible = false;
    }

    public WidgetsDialog(SheetType type) {
        super(type);

        this.isConfiguratorVisible = false;
    }

    public static String getName() {
        return "Widgets";
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        this.activity = (ThemedActivity) context;
        this.manager = AppWidgetManager.getInstance(activity);
        this.widgetStore = activity.getApplicationContext()
                .getSharedPreferences(Entry.WIDGETS);
        this.widgetStackStore = activity.getApplicationContext()
                .getSharedPreferences(Entry.WIDGET_STACKS);

        bindWidgetRequest = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();

                        if (data != null) {
                            int identifier = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

                            if (identifier != -1 && pendingWidgetSize != null) {
                                setupWidget(manager, identifier, pendingWidgetSize);
                            }
                        }
                    }

                    pendingWidgetSize = null;
                });

        stackBindWidgetRequest = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();

                        if (data != null) {
                            int identifier = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

                            if (identifier != -1) {
                                finishStackChildSetup(identifier);
                            }
                        }
                    }
                });
    }

    @Override
    public boolean requiresEagerInitialization() {
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.widget_grid, container, false);

        content = view.findViewById(R.id.content);
        placeholder = view.findViewById(R.id.placeholder);
        grid = view.findViewById(R.id.grid);
        scroller = view.findViewById(R.id.scroller);
        addWidgetContainer = view.findViewById(R.id.add_widget_container);
        FadingEdgeLayout fader = view.findViewById(R.id.fader);

        View.OnClickListener showConfiguratorListener = (v) -> showWidgetPicker();

        placeholder.setOnClickListener(showConfiguratorListener);
        placeholder.findViewById(R.id.add_widget_placeholder)
                .setOnClickListener(showConfiguratorListener);
        addWidgetContainer.findViewById(R.id.add_widget)
                .setOnClickListener(showConfiguratorListener);

        content.setOnLongClickListener(v -> {
            showWidgetPicker();

            return true;
        });

        Measurements.addNavListener(value -> {
            fader.setFadeSizes(Measurements.getSysUIHeight() +
                            (Measurements.isLandscape() ? 0 : Measurements.getDefaultPadding()),
                    0, value + Measurements.getDefaultPadding(), 0);

            content.setPadding(content.getPaddingLeft(), content.getPaddingBottom(),
                    content.getPaddingRight(), value);
        });

        Measurements.addStatusBarListener(value -> {
            fader.setFadeSizes(value +
                            (Measurements.isLandscape() ? 0 : Measurements.getDefaultPadding()),
                    0, Measurements.getNavHeight() + Measurements.getDefaultPadding(), 0);

            content.setPadding(content.getPaddingLeft(), value,
                    content.getPaddingRight(), content.getPaddingBottom());
        });

        setOnBackPressed(() -> {
            hide(true);

            return false;
        });

        PriorityQueue<Widget> widgets = new PriorityQueue<>();

        // Ids that legitimately belong to this host: either a top-level slot
        // (widgetStore) or a child bound inside one of its widget stacks
        // (widgetStackStore). Anything else allocated under this host is a
        // leftover from a previous run and gets cleaned up below. Without
        // counting stack children here, the loop that follows would treat
        // every one of them as an orphan and delete it on every reopen.
        Set<Integer> ownedIdentifiers = new HashSet<>();

        for (String key : widgetStore.getAll().keySet()) {
            try {
                ownedIdentifiers.add(Integer.valueOf(key));
            } catch (NumberFormatException ignored) {
            }
        }

        for (Map.Entry<String, ?> entry : widgetStackStore.getAll().entrySet()) {
            Object value = entry.getValue();

            if (value instanceof String) {
                WidgetStack stack = WidgetStack.deserialize((String) value);

                if (stack != null) {
                    ownedIdentifiers.addAll(stack.children);
                }
            }
        }

        for (int identifier : WidgetsDialog.this.requireWidgetHost().getAppWidgetIds()) {
            if (!ownedIdentifiers.contains(identifier)) {
                WidgetsDialog.this.requireWidgetHost().deleteAppWidgetId(identifier);
            }
        }

        for (String key : widgetStore.getAll().keySet()) {
            String serial = widgetStore.getString(key, null);
            Widget widget = Widget.deserialize(serial);

            if (widget != null) {
                if (widgets.size() < MAX_COUNT) {
                    widgets.add(widget);
                } else {
                    deleteWidgetSlot(widget);
                }
            } else {
                widgetStore.edit().remove(key).apply();
            }
        }

        while (!widgets.isEmpty()) {
            AppWidgetManager manager = AppWidgetManager.getInstance(activity);
            Widget widget = widgets.poll();

            if (widget != null) {
                View host = widget.isStack ? createStackView(widget) : createWidgetView(manager, widget);

                grid.attach(host, widget);

                updatePlaceholderVisibility(View.GONE);
            }
        }

        grid.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                columnSize = grid.computeCellSize());

        return view;
    }

    private void updatePlaceholderVisibility(int visibility) {
        placeholder.setVisibility(visibility);

        if (visibility == View.VISIBLE || Measurements.isLandscape()) {
            addWidgetContainer.setVisibility(View.GONE);
        } else {
            addWidgetContainer.setVisibility(View.VISIBLE);
        }
    }

    public static int getWidgetCellSize() {
        return columnSize;
    }

    private void showWidgetPicker() {
        if (configurator == null) {
            configurator = new WidgetConfigurator(activity, new WidgetConfigurator.Request() {
                @Override
                public void requestAddition(AppWidgetProviderInfo info, WidgetSize size) {
                    addWidget(info, size);
                }

                @Override
                public void requestStackAddition(WidgetSize size) {
                    addWidgetStack(size);
                }
            });

            configurator.setOnDismissListener(dialog -> isConfiguratorVisible = false);
        }

        if (!isConfiguratorVisible) {
            configurator.show();
            isConfiguratorVisible = true;
        }
    }

    private void addWidget(AppWidgetProviderInfo info, WidgetSize size) {
        if (grid.getChildCount() <= MAX_COUNT) {
            int identifier = requireWidgetHost().allocateAppWidgetId();
            boolean allowed = manager.bindAppWidgetIdIfAllowed(identifier, info.getProfile(), info.provider, null);

            if (!allowed) {
                if (bindWidgetRequest != null) {
                    Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);

                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, identifier);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);

                    pendingWidgetSize = size;
                    bindWidgetRequest.launch(intent);
                }
            } else {
                setupWidget(manager, identifier, size);
            }
        }

        configurator.dismiss();
    }

    private void setupWidget(AppWidgetManager manager, int identifier, WidgetSize size) {
        Widget widget = new Widget(identifier, grid.allocatePosition(), size);
        AppWidgetHostView host = createWidgetView(manager, widget);

        if (host.getAppWidgetInfo().configure == null) {
            completeWidgetSetup(widget, host);
        } else {
            try {
                boolean result = activity.addOnActivityResultListener(CONFIGURATION_CODE,
                        (resultCode, intent) -> {
                            if (resultCode == Activity.RESULT_OK) {
                                completeWidgetSetup(widget, host);
                                host.forceLayout();
                            } else {
                                requireWidgetHost().deleteAppWidgetId(host.getAppWidgetId());
                            }

                            activity.removeOnActivityResultListener(CONFIGURATION_CODE);
                        });

                if (!result) {
                    requireWidgetHost().deleteAppWidgetId(host.getAppWidgetId());
                    activity.removeOnActivityResultListener(CONFIGURATION_CODE);
                } else {
                    requireWidgetHost().startAppWidgetConfigureActivityForResult(activity, identifier,
                            Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                            CONFIGURATION_CODE, getActivityOptionsBundle());
                }
            } catch (ActivityNotFoundException exception) {
                completeWidgetSetup(widget, host);
                activity.removeOnActivityResultListener(CONFIGURATION_CODE);

                Log.w(TAG, "No configure activity found for identifier " + identifier);
            }
        }
    }

    private void completeWidgetSetup(Widget widget, AppWidgetHostView host) {
        widgetStore.edit()
                .putString(String.valueOf(widget.id), widget.serialize())
                .apply();

        grid.attach(host, widget);
        updatePlaceholderVisibility(View.GONE);
    }

    // -------------------------------------------------------------------
    // Widget stacks: one grid slot hosting several real, independently
    // bound app widgets as horizontally-swipeable pages (WidgetStackView).
    // The slot's own Widget.id is a real AppWidgetHost id like any other -
    // it just never gets bound to a provider, and is only ever used as the
    // key for its WidgetStack entry (the ordered list of its children's
    // ids) in widgetStackStore.
    // -------------------------------------------------------------------

    private void addWidgetStack(WidgetSize size) {
        if (grid.getChildCount() <= MAX_COUNT) {
            int identifier = requireWidgetHost().allocateAppWidgetId();
            Widget widget = new Widget(identifier, grid.allocatePosition(), size, true);

            saveStack(widget.id, new WidgetStack());
            widgetStore.edit()
                    .putString(String.valueOf(widget.id), widget.serialize())
                    .apply();

            grid.attach(createStackView(widget), widget);
            updatePlaceholderVisibility(View.GONE);
        }

        configurator.dismiss();
    }

    private WidgetStackView createStackView(Widget widget) {
        WidgetStack stack = loadStack(widget.id);

        WidgetStackView view = new WidgetStackView(activity, stack.children, new WidgetStackView.Callback() {
            @Override
            public View createChildView(int appWidgetId) {
                AppWidgetProviderInfo info = manager.getAppWidgetInfo(appWidgetId);

                if (info == null) {
                    return null;
                }

                return requireWidgetHost()
                        .createView(activity.getApplicationContext(), appWidgetId, info);
            }

            @Override
            public void onAddRequested(WidgetStackView stackView) {
                showStackChildPicker(widget, stack, stackView);
            }

            @Override
            public void onRemoveRequested(WidgetStackView stackView, int appWidgetId) {
                requireWidgetHost().deleteAppWidgetId(appWidgetId);

                stack.children.remove(Integer.valueOf(appWidgetId));
                saveStack(widget.id, stack);

                stackView.notifyChildrenChanged();
            }
        });

        view.setOnHeaderLongClickListener(v -> {
            showStackOptionsMenu(view, widget);

            return true;
        });

        return view;
    }

    private void showStackChildPicker(Widget stackWidget, WidgetStack stack, WidgetStackView stackView) {
        pendingStackWidget = stackWidget;
        pendingStack = stack;
        pendingStackView = stackView;

        if (stackConfigurator == null) {
            stackConfigurator = new WidgetConfigurator(activity, new WidgetConfigurator.Request() {
                @Override
                public void requestAddition(AppWidgetProviderInfo info, WidgetSize size) {
                    addStackChild(info);
                }

                @Override
                public void requestStackAddition(WidgetSize size) {
                    // Nesting a stack inside a stack isn't supported.
                    if (stackConfigurator != null) {
                        stackConfigurator.dismiss();
                    }
                }
            });

            stackConfigurator.setOnDismissListener(dialog -> isStackConfiguratorVisible = false);
        }

        if (!isStackConfiguratorVisible) {
            stackConfigurator.show();
            isStackConfiguratorVisible = true;
        }
    }

    private void addStackChild(AppWidgetProviderInfo info) {
        if (pendingStackWidget == null || pendingStack == null || pendingStackView == null) {
            if (stackConfigurator != null) {
                stackConfigurator.dismiss();
            }

            return;
        }

        int identifier = requireWidgetHost().allocateAppWidgetId();
        boolean allowed = manager.bindAppWidgetIdIfAllowed(identifier, info.getProfile(), info.provider, null);

        if (!allowed) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, identifier);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);

            stackBindWidgetRequest.launch(intent);
        } else {
            finishStackChildSetup(identifier);
        }

        if (stackConfigurator != null) {
            stackConfigurator.dismiss();
        }
    }

    private void finishStackChildSetup(int identifier) {
        if (pendingStackWidget == null || pendingStack == null || pendingStackView == null) {
            requireWidgetHost().deleteAppWidgetId(identifier);

            return;
        }

        Widget capturedWidget = pendingStackWidget;
        WidgetStack capturedStack = pendingStack;
        WidgetStackView capturedView = pendingStackView;

        pendingStackWidget = null;
        pendingStack = null;
        pendingStackView = null;

        Runnable complete = () -> {
            capturedStack.children.add(identifier);
            saveStack(capturedWidget.id, capturedStack);

            capturedView.notifyChildrenChanged();
        };

        AppWidgetProviderInfo info = manager.getAppWidgetInfo(identifier);

        if (info == null || info.configure == null) {
            complete.run();
        } else {
            try {
                boolean result = activity.addOnActivityResultListener(CONFIGURATION_CODE,
                        (resultCode, intent) -> {
                            if (resultCode == Activity.RESULT_OK) {
                                complete.run();
                            } else {
                                requireWidgetHost().deleteAppWidgetId(identifier);
                            }

                            activity.removeOnActivityResultListener(CONFIGURATION_CODE);
                        });

                if (!result) {
                    requireWidgetHost().deleteAppWidgetId(identifier);
                    activity.removeOnActivityResultListener(CONFIGURATION_CODE);
                } else {
                    requireWidgetHost().startAppWidgetConfigureActivityForResult(activity, identifier,
                            Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                            CONFIGURATION_CODE, getActivityOptionsBundle());
                }
            } catch (ActivityNotFoundException exception) {
                complete.run();
                activity.removeOnActivityResultListener(CONFIGURATION_CODE);

                Log.w(TAG, "No configure activity found for stack child identifier " + identifier);
            }
        }
    }

    private void showStackOptionsMenu(WidgetStackView view, Widget widget) {
        Vibrations.getInstance().vibrate();

        PopupMenu menu = new PopupMenu(activity);
        Resources resources = getResources();

        menu.add(new PopupMenu.Item(resources.getString(R.string.remove),
                AppCompatResources.getDrawable(activity, R.drawable.ic_delete),
                v -> deleteStack(view, widget))
        );

        menu.add(new PopupMenu.Item(resources.getString(R.string.create_a_widget),
                AppCompatResources.getDrawable(activity, R.drawable.ic_add),
                v -> showWidgetPicker())
        );

        if (widget.size == WidgetSize.SMALL) {
            menu.add(new PopupMenu.Item(resources.getString(R.string.move_left),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_left),
                    v -> moveWidgetLeftRight(widget, +1)));

            menu.add(new PopupMenu.Item(resources.getString(R.string.move_right),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_right),
                    v -> moveWidgetLeftRight(widget, -1)));
        }

        menu.add(new PopupMenu.Item(resources.getString(R.string.move_up),
                AppCompatResources.getDrawable(activity, R.drawable.ic_move_up),
                v -> moveWidgetUpDown(widget, +1)));

        menu.add(new PopupMenu.Item(resources.getString(R.string.move_down),
                AppCompatResources.getDrawable(activity, R.drawable.ic_move_down),
                v -> moveWidgetUpDown(widget, -1)));

        menu.show(activity, view, PopupMenu.PIVOT_CENTER_HORIZONTAL, true);
    }

    private void deleteStack(WidgetStackView view, Widget widget) {
        WidgetStack stack = loadStack(widget.id);

        for (int childId : stack.children) {
            requireWidgetHost().deleteAppWidgetId(childId);
        }

        widgetStackStore.edit().remove(String.valueOf(widget.id)).apply();
        widgetStore.edit().remove(String.valueOf(widget.id)).apply();
        requireWidgetHost().deleteAppWidgetId(widget.id);

        if (view.getParent() instanceof View) {
            grid.removeView((View) view.getParent());
        }

        updatePlaceholderVisibility(grid.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Cleans up a persisted widget slot (top-level widget or stack, with all
     * of its children) that will never be attached to the grid, because
     * MAX_COUNT was already reached when restoring. Unlike deleteStack(),
     * this never touches the grid itself.
     */
    private void deleteWidgetSlot(Widget widget) {
        if (widget.isStack) {
            WidgetStack stack = loadStack(widget.id);

            for (int childId : stack.children) {
                requireWidgetHost().deleteAppWidgetId(childId);
            }

            widgetStackStore.edit().remove(String.valueOf(widget.id)).apply();
        }

        widgetStore.edit().remove(String.valueOf(widget.id)).apply();
        requireWidgetHost().deleteAppWidgetId(widget.id);
    }

    private WidgetStack loadStack(int widgetId) {
        String serial = widgetStackStore.getString(String.valueOf(widgetId), null);
        WidgetStack stack = serial != null ? WidgetStack.deserialize(serial) : null;

        return stack != null ? stack : new WidgetStack();
    }

    private void saveStack(int widgetId, WidgetStack stack) {
        widgetStackStore.edit()
                .putString(String.valueOf(widgetId), stack.serialize())
                .apply();
    }

    private AppWidgetHostView createWidgetView(AppWidgetManager manager, Widget widget) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(widget.id);
        AppWidgetHostView host = requireWidgetHost()
                .createView(activity.getApplicationContext(), widget.id, info);

        host.setOnLongClickListener(v -> {
            Vibrations.getInstance().vibrate();

            PopupMenu menu = new PopupMenu(activity);
            Resources resources = getResources();

            menu.add(new PopupMenu.Item(resources.getString(R.string.remove),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_delete),
                    view -> deleteWidget(host))
            );

            menu.add(new PopupMenu.Item(resources.getString(R.string.create_a_widget),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_add),
                    view -> showWidgetPicker())
            );

            if (info.configure != null) {
                menu.add(new PopupMenu.Item(resources.getString(R.string.configure_widget),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_edit),
                        view -> {
                            if (activity.addOnActivityResultListener(CONFIGURATION_CODE,
                                    (resultCode, intent) -> {
                                        host.forceLayout();
                                        activity.removeOnActivityResultListener(CONFIGURATION_CODE);
                                    })) {
                                requireWidgetHost()
                                        .startAppWidgetConfigureActivityForResult(activity, widget.id,
                                                Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS
                                                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                                                CONFIGURATION_CODE, getActivityOptionsBundle());
                            }
                        })
                );
            }

            if (widget.size == WidgetSize.SMALL) {
                menu.add(new PopupMenu.Item(resources.getString(R.string.move_left),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_move_left),
                        view -> moveWidgetLeftRight(widget, +1)));

                menu.add(new PopupMenu.Item(resources.getString(R.string.move_right),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_move_right),
                        view -> moveWidgetLeftRight(widget, -1)));
            }

            menu.add(new PopupMenu.Item(resources.getString(R.string.move_up),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_up),
                    view -> moveWidgetUpDown(widget, +1)));

            menu.add(new PopupMenu.Item(resources.getString(R.string.move_down),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_down),
                    view -> moveWidgetUpDown(widget, -1)));

            menu.setOnDismissListener(() -> host.animate().scaleY(1)
                    .scaleX(1)
                    .alpha(1)
                    .setDuration(Animation.SHORT.getDuration()));

            menu.show(activity, host, PopupMenu.PIVOT_CENTER_HORIZONTAL, true);

            return true;
        });

        return host;
    }

    private void moveWidgetLeftRight(Widget widget, int direction) {
        if (widget.size != WidgetSize.SMALL) return;

        List<WidgetContainer> list = new ArrayList<>();
        for (int i = 0; i < grid.getChildCount(); i++) {
            list.add((WidgetContainer) grid.getChildAt(i));
        }
        list.sort(Comparator.comparingInt(WidgetContainer::getPosition));

        int targetIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getWidget().id == widget.id) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex == -1) return;

        WidgetContainer wc = list.get(targetIndex);
        int swapIndex = targetIndex + direction;

        if (swapIndex >= 0 && swapIndex < list.size()) {
            WidgetContainer swapWc = list.get(swapIndex);
            if (swapWc.getSize() == WidgetSize.SMALL && swapWc.getOriginRow() == wc.getOriginRow()) {
                Widget swapWidget = swapWc.getWidget();
                int tempPos = widget.position;
                widget.position = swapWidget.position;
                swapWidget.position = tempPos;

                widgetStore.edit()
                        .putString(String.valueOf(widget.id), widget.serialize())
                        .putString(String.valueOf(swapWidget.id), swapWidget.serialize())
                        .apply();

                grid.reorder();
            }
        }
    }

    private void moveWidgetUpDown(Widget widget, int direction) {
        List<WidgetContainer> list = new ArrayList<>();
        for (int i = 0; i < grid.getChildCount(); i++) {
            list.add((WidgetContainer) grid.getChildAt(i));
        }
        list.sort(Comparator.comparingInt(WidgetContainer::getPosition));

        int targetIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getWidget().id == widget.id) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex == -1) return;

        int chunkStart = targetIndex;
        int chunkEnd = targetIndex;

        WidgetContainer wc = list.get(targetIndex);
        if (wc.getSize() == WidgetSize.SMALL) {
            int row = wc.getOriginRow();
            while (chunkStart > 0) {
                WidgetContainer prev = list.get(chunkStart - 1);
                if (prev.getSize() == WidgetSize.SMALL && prev.getOriginRow() == row) {
                    chunkStart--;
                } else {
                    break;
                }
            }
            while (chunkEnd < list.size() - 1) {
                WidgetContainer next = list.get(chunkEnd + 1);
                if (next.getSize() == WidgetSize.SMALL && next.getOriginRow() == row) {
                    chunkEnd++;
                } else {
                    break;
                }
            }
        }

        int swapStart = -1;
        int swapEnd = -1;

        if (direction == -1) { // UP
            if (chunkStart == 0) return;
            swapEnd = chunkStart - 1;
            swapStart = swapEnd;
            WidgetContainer prevWc = list.get(swapEnd);
            if (prevWc.getSize() == WidgetSize.SMALL) {
                int row = prevWc.getOriginRow();
                while (swapStart > 0) {
                    WidgetContainer p = list.get(swapStart - 1);
                    if (p.getSize() == WidgetSize.SMALL && p.getOriginRow() == row) {
                        swapStart--;
                    } else {
                        break;
                    }
                }
            }
        } else { // DOWN
            if (chunkEnd == list.size() - 1) return;
            swapStart = chunkEnd + 1;
            swapEnd = swapStart;
            WidgetContainer nextWc = list.get(swapStart);
            if (nextWc.getSize() == WidgetSize.SMALL) {
                int row = nextWc.getOriginRow();
                while (swapEnd < list.size() - 1) {
                    WidgetContainer n = list.get(swapEnd + 1);
                    if (n.getSize() == WidgetSize.SMALL && n.getOriginRow() == row) {
                        swapEnd++;
                    } else {
                        break;
                    }
                }
            }
        }

        int rangeStart = Math.min(chunkStart, swapStart);
        int rangeEnd = Math.max(chunkEnd, swapEnd);

        List<Integer> positions = new ArrayList<>();
        for (int i = rangeStart; i <= rangeEnd; i++) {
            positions.add(list.get(i).getPosition());
        }

        List<WidgetContainer> newOrder = new ArrayList<>();
        if (direction == -1) {
            for (int i = chunkStart; i <= chunkEnd; i++) newOrder.add(list.get(i));
            for (int i = swapStart; i <= swapEnd; i++) newOrder.add(list.get(i));
        } else {
            for (int i = swapStart; i <= swapEnd; i++) newOrder.add(list.get(i));
            for (int i = chunkStart; i <= chunkEnd; i++) newOrder.add(list.get(i));
        }

        SharedPreferences.Editor editor = widgetStore.edit();
        for (int i = 0; i < newOrder.size(); i++) {
            Widget w = newOrder.get(i).getWidget();
            w.position = positions.get(i);
            editor.putString(String.valueOf(w.id), w.serialize());
        }
        editor.apply();

        grid.reorder();
    }

    private void deleteWidget(AppWidgetHostView host) {
        String identifier = String.valueOf(host.getAppWidgetId());

        if (widgetStore.contains(identifier)) {
            Widget holder =
                    Widget.deserialize(
                            widgetStore.getString(identifier, null));

            if (holder != null) {
                widgetStore.edit()
                        .remove(identifier)
                        .apply();
            }
        }

        requireWidgetHost().deleteAppWidgetId(host.getAppWidgetId());
        grid.removeView((View) (host.getParent()));

        updatePlaceholderVisibility(grid.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    public @NonNull WidgetHost requireWidgetHost() {
        if (host == null) {
            host = new WidgetHost(activity, HOST_ID);

            host.startListening();
        }

        return host;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updatePlaceholderVisibility(placeholder.getVisibility());
    }

    @Override
    public void onStart() {
        super.onStart();

        if (host != null) {
            host.startListening();
        }
    }

    @Override
    public void onStop() {
        scroller.scrollTo(0, 0);

        if (host != null) {
            try {
                host.stopListening();
            } catch (Exception exception) {
                Log.e(TAG, "onStop: " + exception.getMessage());
            }
        }

        super.onStop();
    }

    private static Bundle getActivityOptionsBundle() {
        ActivityOptions activityOptions = ActivityOptions.makeBasic();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            activityOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activityOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        }

        return activityOptions.toBundle();
    }
}
