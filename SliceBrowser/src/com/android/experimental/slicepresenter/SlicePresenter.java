/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.experimental.slicepresenter;

import android.app.Activity;
import android.app.slice.Slice;
import android.app.slice.widget.SliceView;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnSuggestionListener;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.Toolbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SlicePresenter extends Activity {

    private static final String TAG = "SlicePresenter";

    private static final String SLICE_METADATA_KEY = "android.metadata.SLICE_URI";

    private ArrayList<Uri> mSliceUris = new ArrayList<Uri>();
    private String mSelectedMode;
    private ViewGroup mContainer;
    private SearchView mSearchView;
    private SimpleCursorAdapter mAdapter;
    private SubMenu mTypeMenu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        setActionBar(toolbar);

        // Shows the slice
        mContainer = findViewById(R.id.slice_preview);
        mSearchView = findViewById(R.id.search_view);

        final String[] from = new String[]{"uri"};
        final int[] to = new int[]{android.R.id.text1};
        mAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1,
                null, from, to, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        mSearchView.setSuggestionsAdapter(mAdapter);
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionClick(int position) {
                mSearchView.setQuery(((Cursor) mAdapter.getItem(position)).getString(1), true);
                return true;
            }

            @Override
            public boolean onSuggestionSelect(int position) {
                mSearchView.setQuery(((Cursor) mAdapter.getItem(position)).getString(1), true);
                return true;
            }
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                addSlice(Uri.parse(s));
                mSearchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                populateAdapter(s);
                return false;
            }
        });

        mSelectedMode = (savedInstanceState != null)
                ? savedInstanceState.getString("SELECTED_MODE", SliceView.MODE_SHORTCUT)
                : SliceView.MODE_SHORTCUT;
        if (savedInstanceState != null) {
            mSearchView.setQuery(savedInstanceState.getString("SELECTED_QUERY"), true);
        }

        // TODO: Listen for changes.
        updateAvailableSlices();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTypeMenu = menu.addSubMenu("Type");
        mTypeMenu.setIcon(R.drawable.ic_shortcut);
        mTypeMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mTypeMenu.add("Shortcut");
        mTypeMenu.add("Small");
        mTypeMenu.add("Large");
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getTitle().toString()) {
            case "Shortcut":
                mTypeMenu.setIcon(R.drawable.ic_shortcut);
                mSelectedMode = SliceView.MODE_SHORTCUT;
                updateSliceModes();
                return true;
            case "Small":
                mTypeMenu.setIcon(R.drawable.ic_small);
                mSelectedMode = SliceView.MODE_SMALL;
                updateSliceModes();
                return true;
            case "Large":
                mTypeMenu.setIcon(R.drawable.ic_large);
                mSelectedMode = SliceView.MODE_LARGE;
                updateSliceModes();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("SELECTED_MODE", mSelectedMode);
        outState.putString("SELECTED_QUERY", mSearchView.getQuery().toString());
    }

    private void updateAvailableSlices() {
        mSliceUris.clear();
        List<PackageInfo> packageInfos = getPackageManager()
                .getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
        for (PackageInfo pi : packageInfos) {
            ActivityInfo[] activityInfos = pi.activities;
            if (activityInfos != null) {
                for (ActivityInfo ai : activityInfos) {
                    if (ai.metaData != null) {
                        String sliceUri = ai.metaData.getString(SLICE_METADATA_KEY);
                        if (sliceUri != null) {
                            mSliceUris.add(Uri.parse(sliceUri));
                        }
                    }
                }
            }
        }
        populateAdapter(String.valueOf(mSearchView.getQuery()));
    }

    private void addSlice(Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            SliceView v = new SliceView(getApplicationContext());
            v.setTag(uri);
            mContainer.removeAllViews();
            mContainer.addView(v);
            v.setMode(mSelectedMode);
            v.setSlice(uri);
        } else {
            Log.w(TAG, "Invalid uri, skipping slice: " + uri);
        }
    }

    private void updateSliceModes() {
        final int count = mContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            ((SliceView) mContainer.getChildAt(i)).setMode(mSelectedMode);
        }
    }

    private void populateAdapter(String query) {
        final MatrixCursor c = new MatrixCursor(new String[]{BaseColumns._ID, "uri"});
        ArrayMap<String, Integer> ranking = new ArrayMap<>();
        ArrayList<String> suggestions = new ArrayList();
        mSliceUris.forEach(uri -> {
            String uriString = uri.toString();
            if (uriString.contains(query)) {
                ranking.put(uriString, uriString.indexOf(query));
                suggestions.add(uriString);
            }
        });
        suggestions.sort(Comparator.comparingInt(ranking::get));
        for (int i = 0; i < suggestions.size(); i++) {
            c.addRow(new Object[]{i, suggestions.get(i)});
        }
        mAdapter.changeCursor(c);
    }
}
