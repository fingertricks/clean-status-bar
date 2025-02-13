package com.emmaguy.cleanstatusbar;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.emmaguy.cleanstatusbar.prefs.TimePreference;

public class MainActivity extends AppCompatActivity {
    public static int OVERLAY_PERMISSION_REQ_CODE = 1000;

    Switch masterSwitch;

    @Override
    protected void onResume() {
        super.onResume();

        if (!canDrawOverlays()){
            masterSwitch.setChecked(false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initSwitch();

        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(getApplicationContext());
    }

    private void initSwitch() {
        masterSwitch = new Switch(this);
        masterSwitch.setChecked(CleanStatusBarService.isRunning() && canDrawOverlays());
        masterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Intent service = new Intent(MainActivity.this, CleanStatusBarService.class);
                if (b) {
                    if(canDrawOverlays()) {
                        startService(service);
                    } else {
                        compoundButton.setChecked(false);
                        showPermissionSnackBar();
                    }
                } else {
                    stopService(service);
                }
            }
        });

        final ActionBar bar = getSupportActionBar();
        final ActionBar.LayoutParams lp = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.rightMargin = getResources().getDimensionPixelSize(R.dimen.master_switch_margin_right);
        if (bar != null ) {
            bar.setCustomView(masterSwitch, lp);
            bar.setDisplayShowCustomEnabled(true);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            boolean permissionEnabled = Settings.canDrawOverlays(this);

            masterSwitch.setChecked(permissionEnabled);

            if (!permissionEnabled) {
                showPermissionSnackBar();
            }
        }
    }

    private void showPermissionSnackBar() {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.snack_bar_system_alert_window_rationale, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.snack_bar_system_alert_window_action, new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                try {
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                } catch (ActivityNotFoundException e) {
                    // do nothing
                }
            }
        });
        snackbar.show();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        private CleanStatusBarPreferences mPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.prefs);

            initSummary();
            mPreferences = new CleanStatusBarPreferences(getPreferenceManager().getSharedPreferences(), getResources());

            updateEnableKitKatGradientOption();
            updateEnableMLightModeOption();
            updateTimePreference();
        }

        @Override
        public void onResume() {
            super.onResume();

            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePrefsSummary(findPreference(key));

            if (CleanStatusBarService.isRunning()) {
                Intent service = new Intent(getActivity(), CleanStatusBarService.class);
                getActivity().startService(service);
            }

            if (key.equals(getString(R.string.key_api_level))) {
                updateEnableKitKatGradientOption();
                updateEnableMLightModeOption();
            } else if (key.equals(getString(R.string.key_use_24_hour_format))) {
                updateTimePreference();
            }
        }

        private void updateTimePreference() {
            CheckBoxPreference pref = (CheckBoxPreference) findPreference(getString(R.string.key_use_24_hour_format));

            TimePreference timePreference = (TimePreference) findPreference(getString(R.string.key_clock_time));
            timePreference.setIs24HourFormat(pref.isChecked());

            updatePrefsSummary(timePreference);
        }

        private void updateEnableKitKatGradientOption() {
            boolean isKitKat = mPreferences.getApiValue() == Build.VERSION_CODES.KITKAT;
            findPreference(getString(R.string.key_kit_kat_gradient)).setEnabled(isKitKat);
        }

        private void updateEnableMLightModeOption() {
            boolean isM = mPreferences.getApiValue() == Build.VERSION_CODES.M;
            findPreference(getString(R.string.key_m_light_status_bar)).setEnabled(isM);
        }

        protected void initSummary() {
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
                initPrefsSummary(getPreferenceScreen().getPreference(i));
            }
        }

        protected void initPrefsSummary(Preference p) {
            if (p instanceof PreferenceCategory) {
                PreferenceCategory cat = (PreferenceCategory) p;
                for (int i = 0; i < cat.getPreferenceCount(); i++) {
                    initPrefsSummary(cat.getPreference(i));
                }
            } else {
                updatePrefsSummary(p);
            }
        }

        protected void updatePrefsSummary(Preference pref) {
            if (pref == null) {
                return;
            }

            if (pref instanceof ListPreference) {
                ListPreference lst = (ListPreference) pref;
                String currentValue = lst.getValue();

                int index = lst.findIndexOfValue(currentValue);
                CharSequence[] entries = lst.getEntries();
                CharSequence[] entryValues = lst.getEntryValues();
                if (index >= 0 && index < entries.length) {
                    // Show info explaining that the small letters e.g. 3G/LTE etc are only shown when WiFi is off - this is standard Android behaviour
                    boolean currentValueIsOffOrEmpty = currentValue.equals(entryValues[0]) || currentValue.equals(entryValues[1]);
                    if (pref.getKey().equals(getString(R.string.key_signal_3g)) && !currentValueIsOffOrEmpty) {
                        pref.setSummary(entries[index] + " - " + getString(R.string.network_icon_info));
                    } else {
                        pref.setSummary(entries[index]);
                    }
                }
            } else if (pref instanceof TimePreference) {
                if (pref.getKey().equals(getString(R.string.key_clock_time))) {
                    String time = ((TimePreference) pref).getTime();
                    pref.setSummary(time);
                }
            }
        }
    }
}
