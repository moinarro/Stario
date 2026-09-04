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

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import java.util.List;

/**
 * A plain, non-Fragment PagerAdapter over a fixed set of already-inflated
 * page Views - the Dashboard's tabs are static content (no per-tab
 * lifecycle, network calls, or back-stack needs), so there's no reason to
 * pull in child fragments the way BriefingDialog/HideApplicationsDialog do
 * for their genuinely dynamic pages. CenterTabLayout only needs
 * getPageTitle() from whatever PagerAdapter it's given, so it works with
 * this exactly the same as with a fragment-backed one.
 */
class DashboardPagerAdapter extends PagerAdapter {
    private final List<View> pages;
    private final List<CharSequence> titles;

    DashboardPagerAdapter(List<View> pages, List<CharSequence> titles) {
        this.pages = pages;
        this.titles = titles;
    }

    @Override
    public int getCount() {
        return pages.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        View page = pages.get(position);

        if (page.getParent() != container) {
            container.addView(page);
        }

        return page;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return titles.get(position);
    }
}
