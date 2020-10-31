/*
 * Copyright 2011 Google Inc.
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

package de.dennisguse.opentracks.io.file.importer;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.io.file.ErrorListDialog;

/**
 * An activity to import files from the external storage.
 *
 * @author Rodrigo Damazio
 */
public class ImportActivity extends FragmentActivity {

    private static final String TAG = ImportActivity.class.getSimpleName();

    public static final String EXTRA_DIRECTORY_URI_KEY = "directory_uri";

    private static final String BUNDLE_TOOLBAR_TITLE = "toolbar_title";
    private static final String BUNDLE_DOCUMENT_URI = "document_uri";
    private static final String BUNDLE_IS_DIRECTORY = "is_directory";

    private TextView viewTotal;
    private TextView viewDone;
    private TextView viewSummary;
    private ProgressBar viewProgressBar;
    private ImageView viewAlertIcon;
    private TextView viewAlertMsg;
    private Button viewLeftButton;
    private Button viewRightButton;

    boolean doubleBackToCancel = false;

    private Uri documentUri;
    private boolean isDirectory;
    private String toolbarTitle;

    private ImportViewModel viewModel;
    private ImportViewModel.Summary summary;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_progress_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);

        viewTotal = findViewById(R.id.import_progress_total);
        viewDone = findViewById(R.id.import_progress_done);
        viewSummary = findViewById(R.id.import_progress_summary);
        viewProgressBar = findViewById(R.id.import_progress_bar);
        viewAlertIcon = findViewById(R.id.import_progress_alert_icon);
        viewAlertMsg = findViewById(R.id.import_progress_alert_msg);
        viewLeftButton = findViewById(R.id.import_progress_left_button);
        viewRightButton = findViewById(R.id.import_progress_right_button);

        final DocumentFile documentFile;

        if (savedInstanceState == null) {
            if (getIntent().getData() != null) {
                documentUri = getIntent().getData();
                isDirectory = false;
            } else if (getIntent().getClipData() != null && getIntent().getClipData().getItemCount() > 0) {
                documentUri = getIntent().getClipData().getItemAt(0).getUri();
                isDirectory = false;
            } else {
                // Started from DirectoryChooserActivity
                documentUri = getIntent().getParcelableExtra(EXTRA_DIRECTORY_URI_KEY);
                isDirectory = true;
            }

            documentFile = isDirectory ? DocumentFile.fromTreeUri(this, documentUri) : DocumentFile.fromSingleUri(this, documentUri);
            toolbarTitle = getString(R.string.import_progress_message, documentFile.getName());
        } else {
            documentUri = savedInstanceState.getParcelable(BUNDLE_DOCUMENT_URI);
            toolbarTitle = savedInstanceState.getString(BUNDLE_TOOLBAR_TITLE);
            isDirectory = savedInstanceState.getBoolean(BUNDLE_IS_DIRECTORY);

            documentFile = isDirectory ? DocumentFile.fromTreeUri(this, documentUri) : DocumentFile.fromSingleUri(this, documentUri);
        }

        toolbar.setTitle(toolbarTitle);
        initViews();

        viewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        viewModel.getImportData(documentFile).observe(this, data -> {
            summary = data;
            setProgress();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BUNDLE_DOCUMENT_URI, documentUri);
        outState.putBoolean(BUNDLE_IS_DIRECTORY, isDirectory);
        outState.putString(BUNDLE_TOOLBAR_TITLE, toolbarTitle);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToCancel || getTotalDone() == summary.getTotalCount()) {
            super.onBackPressed();
            viewModel.cancel();
            getViewModelStore().clear();
            return;
        }

        this.doubleBackToCancel = true;
        Toast.makeText(this, getString(R.string.generic_click_twice_cancel), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> doubleBackToCancel=false, 2000);
    }

    private void initViews() {
        viewDone.setText("0");
        viewTotal.setText("0");
        viewSummary.setText(getString(R.string.import_progress_review, 0, 0, 0, 0));
    }

    private int getTotalDone() {
        return summary.getSuccessCount() + summary.getExistsCount() + summary.getErrorCount();
    }

    private void setProgress() {
        int done = getTotalDone();

        viewDone.setText("" + done);
        viewTotal.setText("" + summary.getTotalCount());

        viewProgressBar.setProgress((int) ((float) done / (float) summary.getTotalCount() * 100f));
        viewSummary.setText(getString(R.string.import_progress_review, getTotalDone(), summary.getSuccessCount(), summary.getExistsCount(), summary.getErrorCount()));

        if (done == summary.getTotalCount()) {
            onImportEnded();
        }
    }

    private void onImportEnded() {
        viewAlertIcon.setVisibility(View.VISIBLE);
        viewAlertMsg.setVisibility(View.VISIBLE);
        viewRightButton.setVisibility(View.VISIBLE);
        viewRightButton.setText(getString(R.string.generic_ok));

        if (summary.getErrorCount() > 0) {
            viewLeftButton.setVisibility(View.VISIBLE);
            viewLeftButton.setText(getString(R.string.generic_show_errors));
            viewLeftButton.setOnClickListener((view) -> ErrorListDialog.showDialog(getSupportFragmentManager(), getString(R.string.import_error_list_dialog_title), summary.getFileErrors()));
            viewAlertIcon.setImageDrawable(getDrawable(R.drawable.ic_report_problem_24));
            String msg = getResources().getQuantityString(R.plurals.generic_completed_with_errors, summary.getErrorCount(), summary.getErrorCount());
            viewAlertMsg.setText(msg);
        } else {
            viewLeftButton.setVisibility(View.GONE);
            viewAlertIcon.setImageDrawable(getDrawable(R.drawable.ic_dialog_success_24dp));
            viewAlertMsg.setText(getString(R.string.generic_completed));
        }

        viewRightButton.setOnClickListener((view) -> {
            getViewModelStore().clear();
            finish();
        });
    }
}
