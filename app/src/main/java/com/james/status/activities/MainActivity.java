/*
 *    Copyright 2019 James Fenn
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.james.status.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.james.status.R;
import com.james.status.Status;
import com.james.status.adapters.SimplePagerAdapter;
import com.james.status.data.PreferenceData;
import com.james.status.data.icon.IconData;
import com.james.status.fragments.AppPreferenceFragment;
import com.james.status.fragments.GeneralPreferenceFragment;
import com.james.status.fragments.HelpFragment;
import com.james.status.fragments.IconPreferenceFragment;
import com.james.status.services.StatusServiceImpl;
import com.james.status.utils.InfoUtils;
import com.james.status.utils.StaticUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.ViewPager;

public class MainActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener, CompoundButton.OnCheckedChangeListener {

    public static final String ACTION_TOO_MANY_ICONS = "com.james.status.MainActivity.TOO_MANY_ICONS";
    public static final String EXTRA_MANY_ICONS = "com.james.status.MainActivity.EXTRA_MANY_ICONS";

    private Status status;

    private SwitchCompat service;
    private SearchView searchView;

    private AppBarLayout appbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private SimplePagerAdapter adapter;
    private View bottomSheet;
    ImageView expand;
    private TextView title, content;
    private ImageView icon;
    private FloatingActionButton fab;

    private BottomSheetBehavior behavior;
    private MenuItem resetItem;

    private TooManyIconsReceiver tooManyIconsReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(Status.Theme.ACTIVITY_NORMAL.getTheme(this));
        setContentView(R.layout.activity_main);

        status = (Status) getApplicationContext();
        setSupportActionBar(findViewById(R.id.toolbar));

        appbar = findViewById(R.id.appbar);
        tabLayout = findViewById(R.id.tabLayout);
        service = findViewById(R.id.serviceEnabled);
        viewPager = findViewById(R.id.viewPager);
        bottomSheet = findViewById(R.id.bottomSheet);
        expand = findViewById(R.id.expand);
        title = findViewById(R.id.title);
        content = findViewById(R.id.content);
        icon = findViewById(R.id.icon);
        fab = findViewById(R.id.fab);

        adapter = new SimplePagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new GeneralPreferenceFragment(), getString(R.string.preference_general));
        adapter.addFragment(new IconPreferenceFragment(), getString(R.string.preference_icon));
        adapter.addFragment(new AppPreferenceFragment(), getString(R.string.preference_app));
        adapter.addFragment(new HelpFragment(), getString(R.string.preference_help));

        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(this);
        tabLayout.setupWithViewPager(viewPager);

        service.setChecked((boolean) PreferenceData.STATUS_ENABLED.getValue(this));
        service.setOnCheckedChangeListener(this);

        if (StaticUtils.shouldShowTutorial(this, "mainActivity"))
            setTutorial(R.string.tutorial_main, R.string.tutorial_main_desc, new int[]{R.id.title}, true);

        behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setHideable(true);
        if (!StaticUtils.isAllPermissionsGranted(this))
            behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        else behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        expand.setOnClickListener(v -> {
            if (behavior.getState() == BottomSheetBehavior.STATE_COLLAPSED)
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            else if (behavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });

        fab.setOnClickListener(v ->
                startActivityForResult(new Intent(MainActivity.this, ImagePickerActivity.class), 0)
        );

        tooManyIconsReceiver = new TooManyIconsReceiver();
        registerReceiver(tooManyIconsReceiver, new IntentFilter(ACTION_TOO_MANY_ICONS));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(tooManyIconsReceiver);
    }

    private void setTutorial(@StringRes int title, @StringRes int content, @Nullable int[] ids, boolean showSkip) {
        this.title.setText(title);
        this.content.setText(content);

        BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked)
            StatusServiceImpl.start(this);
        else
            StatusServiceImpl.stop(this);

        PreferenceData.STATUS_ENABLED.setValue(this, isChecked);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        resetItem = menu.findItem(R.id.action_reset);
        searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(viewPager.getCurrentItem(), query.toLowerCase());
                appbar.setExpanded(true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(viewPager.getCurrentItem(), newText.toLowerCase());
                appbar.setExpanded(true);
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            adapter.filter(viewPager.getCurrentItem(), null);
            return false;
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_setup:
                startActivity(new Intent(this, StartActivity.class));
                break;
            case R.id.action_tutorial:
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = prefs.edit();
                for (String key : prefs.getAll().keySet()) {
                    if (key.startsWith("tutorial")) editor.remove(key);
                }

                editor.apply();
                break;
            case R.id.action_about:
                startActivity(new Intent(this, HelpFragment.class));
                break;
            case R.id.action_reset:
                if (adapter.getItem(viewPager.getCurrentItem()) instanceof AppPreferenceFragment)
                    ((AppPreferenceFragment) adapter.getItem(viewPager.getCurrentItem())).reset();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (StaticUtils.isAllPermissionsGranted(this) && !behavior.isHideable()) {
            behavior.setHideable(true);
            behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        status.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        if (resetItem != null) {
            if (adapter.getItem(position) instanceof AppPreferenceFragment) {
                fab.show();
                resetItem.setVisible(true);
            } else {
                fab.hide();
                resetItem.setVisible(false);
            }
        }

        if (behavior != null && behavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            switch (position) {
                case 1:
                    if (StaticUtils.shouldShowTutorial(this, "disableicon")) {
                        setTutorial(R.string.tutorial_icon_switch, R.string.tutorial_icon_switch_desc, null, false);
                    } else if (StaticUtils.shouldShowTutorial(this, "moveicon", 1)) {
                        setTutorial(R.string.tutorial_icon_order, R.string.tutorial_icon_order_desc, null, false);
                    }
                    break;
                case 2:
                    if (StaticUtils.shouldShowTutorial(this, "notify"))
                        setTutorial(R.string.tutorial_app_notification, R.string.tutorial_app_notification_desc, null, false);
                    break;
            }
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    private class TooManyIconsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isTooMany = intent.getBooleanExtra(EXTRA_MANY_ICONS, false);

            if (isTooMany) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.dialog_too_many_icons)
                        .setMessage(R.string.dialog_too_many_icons_desc)
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(InfoUtils.SUPPORT_URL)));
                            dialog.dismiss();
                        })
                        .show();
            }
        }
    }
}
