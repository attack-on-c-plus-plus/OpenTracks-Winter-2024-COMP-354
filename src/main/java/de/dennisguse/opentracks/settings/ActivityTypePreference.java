/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.settings;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragment;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.fragments.ChooseActivityTypeDialogFragment;
import de.dennisguse.opentracks.util.PreferencesUtils;
import de.dennisguse.opentracks.util.TrackIconUtils;

/**
 * For entering the default activity type.
 *
 * @author Jimmy Shih
 */
public class ActivityTypePreference extends DialogPreference {

    private SummaryProvider<DialogPreference> summaryProvider = new SummaryProvider<DialogPreference>() {
        @Override
        public CharSequence provideSummary(DialogPreference preference) {
            String defaultActivity = PreferencesUtils.getString(ActivityTypePreference.this.getContext(), R.string.default_activity_key, PreferencesUtils.DEFAULT_ACTIVITY_DEFAULT);
            return defaultActivity != null && !defaultActivity.equals(PreferencesUtils.DEFAULT_ACTIVITY_DEFAULT) ? defaultActivity : getContext().getString(R.string.value_unknown);
        }
    };

    public ActivityTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.preference_activity_type);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
        setPersistent(true);
        //TODO: for some reason the summary is not updated; after changing it. Enable summaryprovider, when fixed.
//        setSummaryProvider(summaryProvider);
    }

    @Override
    public int getDialogLayoutResource() {
        return R.layout.preference_activity_type;
    }

    public static class ActivityPreferenceDialog extends PreferenceDialogFragment {

        private AutoCompleteTextView textView;
        private Spinner spinner;

        static ActivityPreferenceDialog newInstance(String preferenceKey) {
            ActivityTypePreference.ActivityPreferenceDialog dialog = new ActivityTypePreference.ActivityPreferenceDialog();
            final Bundle bundle = new Bundle(1);
            bundle.putString(PreferenceDialogFragment.ARG_KEY, preferenceKey);
            dialog.setArguments(bundle);

            return dialog;
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);

            final Context context = getActivity();

            textView = view.findViewById(R.id.activity_type_preference_text_view);
            String category = PreferencesUtils.getString(context, R.string.default_activity_key, PreferencesUtils.DEFAULT_ACTIVITY_DEFAULT);
            textView.setText(category);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context, R.array.activity_types, android.R.layout.simple_dropdown_item_1line);
            textView.setAdapter(adapter);
            textView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    String iconValue = TrackIconUtils.getIconValue(context, (String) textView.getAdapter().getItem(position));
                    TrackIconUtils.setIconSpinner(spinner, iconValue);
                }
            });
            textView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        String iconValue = TrackIconUtils.getIconValue(context, textView.getText().toString());
                        TrackIconUtils.setIconSpinner(spinner, iconValue);
                    }
                }
            });

            String iconValue = TrackIconUtils.getIconValue(context, category);
            spinner = view.findViewById(R.id.activity_type_preference_spinner);
            spinner.setAdapter(TrackIconUtils.getIconSpinnerAdapter(context, iconValue));
            spinner.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showIconSelectDialog();
                    }
                    return true;
                }
            });
            spinner.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        showIconSelectDialog();
                    }
                    return true;
                }
            });
        }

        private void showIconSelectDialog() {
            String category = PreferencesUtils.getString(getActivity(), R.string.default_activity_key, PreferencesUtils.DEFAULT_ACTIVITY_DEFAULT);

            // TODO Remove casting when upgrading to new Androidx compat.
            FragmentActivity fragmentActivity = (androidx.fragment.app.FragmentActivity) getActivity();
            ChooseActivityTypeDialogFragment.showDialog(fragmentActivity.getSupportFragmentManager(), category);
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                String newDefaultActivity = textView.getText().toString();
                if (getPreference().callChangeListener(newDefaultActivity)) {
                    PreferencesUtils.setString(getActivity(), R.string.default_activity_key, newDefaultActivity);
                }
            }
        }

        /**
         * Updates the value of the dialog.
         *
         * @param iconValue the icon value
         */
        public void updateUI(String iconValue) {
            TrackIconUtils.setIconSpinner(spinner, iconValue);
            textView.setText(getActivity().getString(TrackIconUtils.getIconActivityType(iconValue)));
            textView.clearFocus();
        }
    }
}
