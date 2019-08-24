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

package de.dennisguse.opentracks;

import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import de.dennisguse.opentracks.content.ContentProviderUtils;
import de.dennisguse.opentracks.content.SearchEngine;
import de.dennisguse.opentracks.content.SearchEngine.ScoredResult;
import de.dennisguse.opentracks.content.SearchEngine.SearchQuery;
import de.dennisguse.opentracks.content.Track;
import de.dennisguse.opentracks.content.Waypoint;
import de.dennisguse.opentracks.content.Waypoint.WaypointType;
import de.dennisguse.opentracks.fragments.ConfirmDeleteDialogFragment;
import de.dennisguse.opentracks.fragments.DeleteMarkerDialogFragment;
import de.dennisguse.opentracks.fragments.DeleteMarkerDialogFragment.DeleteMarkerCaller;
import de.dennisguse.opentracks.services.TrackRecordingServiceConnection;
import de.dennisguse.opentracks.stats.TripStatistics;
import de.dennisguse.opentracks.util.IntentUtils;
import de.dennisguse.opentracks.util.ListItemUtils;
import de.dennisguse.opentracks.util.PreferencesUtils;
import de.dennisguse.opentracks.util.StringUtils;
import de.dennisguse.opentracks.util.TrackIconUtils;
import de.dennisguse.opentracks.util.TrackRecordingServiceConnectionUtils;

/**
 * An activity to display a list of search results.
 *
 * @author Rodrigo Damazio
 * <p>
 * TODO: allow to refine search (present search in context menu)
 */
public class SearchListActivity extends AbstractListActivity implements DeleteMarkerCaller, ConfirmDeleteDialogFragment.ConfirmDeleteCaller {

    private static final String TAG = SearchListActivity.class.getSimpleName();

    private static final String IS_RECORDING_FIELD = "isRecording";
    private static final String IS_PAUSED_FIELD = "isPaused";
    private static final String ICON_ID_FIELD = "icon";
    private static final String ICON_CONTENT_DESCRIPTION_ID_FIELD = "iconContentDescription";
    private static final String NAME_FIELD = "name";
    private static final String TOTAL_TIME_FIELD = "totalTime";
    private static final String TOTAL_DISTANCE_FIELD = "totalDistance";
    private static final String MARKER_COUNT_FIELD = "markerCount";
    private static final String START_TIME_FIELD = "startTime";
    private static final String CATEGORY_FIELD = "category";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String PHOTO_URL_FIELD = "photoUrl";
    private static final String MARKER_LATITUDE_FIELD = "latitude";
    private static final String MARKER_LONGITUDE_FIELD = "longitude";

    private static final String TRACK_ID_FIELD = "trackId";
    private static final String MARKER_ID_FIELD = "markerId";
    private ContentProviderUtils contentProviderUtils;
    private SharedPreferences sharedPreferences;
    private TrackRecordingServiceConnection trackRecordingServiceConnection;
    private SearchEngine searchEngine;
    private ArrayAdapter<Map<String, Object>> arrayAdapter;
    private boolean metricUnits = true;
    private long recordingTrackId = PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;
    // Callback when an item is selected in the contextual action mode
    private ContextualActionModeCallback contextualActionModeCallback = new ContextualActionModeCallback() {
        @Override
        public void onPrepare(Menu menu, int[] positions, long[] ids, boolean showSelectAll) {
            boolean isRecording = recordingTrackId != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;
            boolean isSingleSelection = positions.length == 1;
            boolean isSingleSelectionTrack;
            if (isSingleSelection) {
                Map<String, Object> item = arrayAdapter.getItem(positions[0]);
                Long trackId = (Long) item.get(TRACK_ID_FIELD);
                Track track = contentProviderUtils.getTrack(trackId);

                isSingleSelectionTrack = item.get(MARKER_ID_FIELD) == null;
            } else {
                isSingleSelectionTrack = false;
            }
            // Not recording, one item, item is a track
            menu.findItem(R.id.list_context_menu_share).setVisible(!isRecording && isSingleSelection && isSingleSelectionTrack);
            //TODO Setup shareIntent.

            // One item, item is a marker
            menu.findItem(R.id.list_context_menu_show_on_map).setVisible(isSingleSelection && !isSingleSelectionTrack);
            // One item, can be a track or a marker
            menu.findItem(R.id.list_context_menu_edit).setVisible(isSingleSelection);
            // One item. If track, no restriction.
            menu.findItem(R.id.list_context_menu_delete).setVisible(isSingleSelection && (isSingleSelectionTrack));
            // Disable select all, no action is available for multiple selection
            menu.findItem(R.id.list_context_menu_select_all).setVisible(false);
        }

        @Override
        public boolean onClick(int itemId, int[] positions, long[] ids) {
            return handleContextItem(itemId, positions);
        }
    };
    private boolean recordingTrackPaused = PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT;
    private final OnSharedPreferenceChangeListener
            sharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key == null || key.equals(PreferencesUtils.getKey(SearchListActivity.this, R.string.stats_units_key))) {
                metricUnits = PreferencesUtils.isMetricUnits(SearchListActivity.this);
            }
            if (key == null || key.equals(PreferencesUtils.getKey(SearchListActivity.this, R.string.recording_track_id_key))) {
                recordingTrackId = PreferencesUtils.getLong(SearchListActivity.this, R.string.recording_track_id_key);
            }
            if (key == null || key.equals(PreferencesUtils.getKey(SearchListActivity.this, R.string.recording_track_paused_key))) {
                recordingTrackPaused = PreferencesUtils.getBoolean(SearchListActivity.this, R.string.recording_track_paused_key, PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT);
            }
            if (key != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        arrayAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        contentProviderUtils = ContentProviderUtils.Factory.get(this);
        sharedPreferences = PreferencesUtils.getSharedPreferences(this);
        trackRecordingServiceConnection = new TrackRecordingServiceConnection(this, null);
        searchEngine = new SearchEngine(contentProviderUtils);

        arrayAdapter = new ArrayAdapter<Map<String, Object>>(this, R.layout.list_item, R.id.list_item_name) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = getLayoutInflater().inflate(R.layout.list_item, parent, false);
                } else {
                    view = convertView;
                }
                Map<String, Object> resultMap = getItem(position);
                boolean isRecording = (boolean) resultMap.get(IS_RECORDING_FIELD);
                boolean isPaused = (boolean) resultMap.get(IS_PAUSED_FIELD);
                int iconId = (int) resultMap.get(ICON_ID_FIELD);
                int iconContentDescriptionId = (int) resultMap.get(ICON_CONTENT_DESCRIPTION_ID_FIELD);
                String name = (String) resultMap.get(NAME_FIELD);
                String totalTime = (String) resultMap.get(TOTAL_TIME_FIELD);
                String totalDistance = (String) resultMap.get(TOTAL_DISTANCE_FIELD);
                int markerCount = (int) resultMap.get(MARKER_COUNT_FIELD);
                long startTime = (long) resultMap.get(START_TIME_FIELD);
                String category = (String) resultMap.get(CATEGORY_FIELD);
                String description = (String) resultMap.get(DESCRIPTION_FIELD);
                String photoUrl = (String) resultMap.get(PHOTO_URL_FIELD);

                ListItemUtils.setListItem(SearchListActivity.this, view, isRecording, isPaused, iconId,
                        iconContentDescriptionId, name, totalTime, totalDistance, markerCount,
                        startTime, false, category, description, photoUrl);
                return view;
            }
        };
        // UI elements
        ListView listView = findViewById(R.id.search_list);
        listView.setAdapter(arrayAdapter);
        listView.setEmptyView(findViewById(R.id.search_list_empty));
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Map<String, Object> item = arrayAdapter.getItem(position);
                Long trackId = (Long) item.get(TRACK_ID_FIELD);
                Long markerId = (Long) item.get(MARKER_ID_FIELD);
                Intent intent;
                if (markerId != null) {
                    intent = IntentUtils.newIntent(SearchListActivity.this, TrackDetailActivity.class)
                            .putExtra(TrackDetailActivity.EXTRA_MARKER_ID, markerId);
                } else {
                    intent = IntentUtils.newIntent(SearchListActivity.this, TrackDetailActivity.class)
                            .putExtra(TrackDetailActivity.EXTRA_TRACK_ID, trackId);
                }
                startActivity(intent);
            }
        });
        AbstractListActivity.configureListViewContextualMenu(listView, contextualActionModeCallback);
        handleIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        sharedPreferenceChangeListener.onSharedPreferenceChanged(null, null);
        TrackRecordingServiceConnectionUtils.startConnection(this, trackRecordingServiceConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        arrayAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        trackRecordingServiceConnection.unbind();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.search_list;
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_list, menu);
        menu.findItem(R.id.search_list_search).setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.search_list_search) {
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.list_context_menu, menu);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        contextualActionModeCallback.onPrepare(menu, new int[]{info.position}, new long[]{info.id}, false);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if (handleContextItem(item.getItemId(), new int[]{info.position})) {
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Handles a context item selection.
     *
     * @param itemId    the menu item id
     * @param positions the positions of the selected rows
     * @return true if handled.
     */
    private boolean handleContextItem(int itemId, int[] positions) {
        if (positions.length != 1) {
            return false;
        }
        Map<String, Object> item = arrayAdapter.getItem(positions[0]);
        Long trackId = (Long) item.get(TRACK_ID_FIELD);
        Long markerId = (Long) item.get(MARKER_ID_FIELD);
        Intent intent;
        switch (itemId) {
            case R.id.list_context_menu_share:
                //TODO
                Log.e(TAG, "Not implemented");
                return true;
            case R.id.list_context_menu_show_on_map:
                intent = IntentUtils.newShowCoordinateOnMapIntent((double) item.get(MARKER_LATITUDE_FIELD), (double) item.get(MARKER_LONGITUDE_FIELD), item.get(NAME_FIELD) + "");
                startActivity(intent);
                return true;
            case R.id.list_context_menu_edit:
                if (markerId != null) {
                    intent = IntentUtils.newIntent(this, MarkerEditActivity.class)
                            .putExtra(MarkerEditActivity.EXTRA_MARKER_ID, markerId);
                } else {
                    intent = IntentUtils.newIntent(this, TrackEditActivity.class)
                            .putExtra(TrackEditActivity.EXTRA_TRACK_ID, trackId);
                }
                startActivity(intent);

                // Close the search result since its content can change after edit.
                finish();
                return true;
            case R.id.list_context_menu_delete:
                if (markerId != null) {
                    DeleteMarkerDialogFragment.newInstance(new long[]{markerId}).show(getSupportFragmentManager(), DeleteMarkerDialogFragment.DELETE_MARKER_DIALOG_TAG);
                } else {
                    deleteTracks(new long[]{trackId});
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Handles the intent.
     *
     * @param intent the intent
     */
    private void handleIntent(Intent intent) {
        if (!Intent.ACTION_SEARCH.equals(intent.getAction())) {
            Log.e(TAG, "Invalid intent action: " + intent);
            finish();
            return;
        }

        final String textQuery = intent.getStringExtra(SearchManager.QUERY);
        setTitle(textQuery);

        doSearch(textQuery);
    }

    /**
     * Do the search.
     *
     * @param textQuery the query
     */
    private void doSearch(String textQuery) {
        SearchQuery query = new SearchQuery(textQuery, null, -1L, System.currentTimeMillis());
        SortedSet<ScoredResult> scoredResults = searchEngine.search(query);
        final List<Map<String, Object>> displayResults = prepareResultsforDisplay(scoredResults);

        // Use the UI thread to display the results
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                arrayAdapter.clear();
                arrayAdapter.addAll(displayResults);
            }
        });
    }

    /**
     * Prepares the result for display.
     *
     * @param scoredResults a list of score results
     * @return a list of result maps
     */
    private List<Map<String, Object>> prepareResultsforDisplay(Collection<ScoredResult> scoredResults) {
        //TODO Replace use of map<string, object>, but rather provide Track or Waypoint directly.
        ArrayList<Map<String, Object>> output = new ArrayList<>(scoredResults.size());
        for (ScoredResult result : scoredResults) {
            Map<String, Object> resultMap = new HashMap<>();
            if (result.track != null) {
                prepareTrackForDisplay(result.track, resultMap);
            } else {
                prepareMarkerForDisplay(result.waypoint, resultMap);
            }
            output.add(resultMap);
        }
        return output;
    }

    /**
     * Prepares a marker for display by filling in a result map.
     *
     * @param waypoint  the marker
     * @param resultMap the result map
     */
    @Deprecated
    private void prepareMarkerForDisplay(Waypoint waypoint, Map<String, Object> resultMap) {
        //TODO: It may be more appropriate to obtain the track name as a join in the retrieval phase of the search.
        String trackName = null;
        long trackId = waypoint.getTrackId();
        if (trackId != -1L) {
            Track track = contentProviderUtils.getTrack(trackId);
            if (track != null) {
                trackName = track.getName();
            }
        }

        boolean statistics = waypoint.getType() == WaypointType.STATISTICS;

        resultMap.put(IS_RECORDING_FIELD, false);
        resultMap.put(IS_PAUSED_FIELD, true);
        resultMap.put(ICON_ID_FIELD, statistics ? R.drawable.ic_marker_yellow_pushpin : R.drawable.ic_marker_blue_pushpin);
        resultMap.put(ICON_CONTENT_DESCRIPTION_ID_FIELD, R.string.image_marker);
        resultMap.put(NAME_FIELD, waypoint.getName());
        // Display the marker's track name in the total time field
        resultMap.put(TOTAL_TIME_FIELD, trackName == null ? null : getString(R.string.search_list_marker_track_location, trackName));
        resultMap.put(TOTAL_DISTANCE_FIELD, null);
        resultMap.put(MARKER_COUNT_FIELD, 0);
        resultMap.put(START_TIME_FIELD, waypoint.getLocation().getTime());
        resultMap.put(CATEGORY_FIELD, statistics ? null : waypoint.getCategory());
        resultMap.put(DESCRIPTION_FIELD, statistics ? null : waypoint.getDescription());
        resultMap.put(PHOTO_URL_FIELD, waypoint.getPhotoUrl());
        resultMap.put(TRACK_ID_FIELD, waypoint.getTrackId());
        resultMap.put(MARKER_ID_FIELD, waypoint.getId());

        resultMap.put(MARKER_LATITUDE_FIELD, waypoint.getLocation().getLatitude());
        resultMap.put(MARKER_LONGITUDE_FIELD, waypoint.getLocation().getLongitude());
    }

    /**
     * Prepares a track for display by filling in a result map.
     *
     * @param track     the track
     * @param resultMap the result map
     */
    @Deprecated
    private void prepareTrackForDisplay(Track track, Map<String, Object> resultMap) {
        TripStatistics tripStatitics = track.getTripStatistics();
        String icon = track.getIcon();
        String category = icon != null && !icon.equals("") ? null : track.getCategory();

        resultMap.put(IS_RECORDING_FIELD, track.getId() == recordingTrackId);
        resultMap.put(IS_PAUSED_FIELD, recordingTrackPaused);
        resultMap.put(ICON_ID_FIELD, TrackIconUtils.getIconDrawable(icon));
        resultMap.put(ICON_CONTENT_DESCRIPTION_ID_FIELD, R.string.image_track);
        resultMap.put(NAME_FIELD, track.getName());
        resultMap.put(TOTAL_TIME_FIELD, StringUtils.formatElapsedTime(tripStatitics.getTotalTime()));
        resultMap.put(TOTAL_DISTANCE_FIELD, StringUtils.formatDistance(this, tripStatitics.getTotalDistance(), metricUnits));
        resultMap.put(MARKER_COUNT_FIELD, contentProviderUtils.getWaypointCount(track.getId()));
        resultMap.put(START_TIME_FIELD, tripStatitics.getStartTime());
        resultMap.put(CATEGORY_FIELD, category);
        resultMap.put(DESCRIPTION_FIELD, track.getDescription());
        resultMap.put(PHOTO_URL_FIELD, null);
        resultMap.put(TRACK_ID_FIELD, track.getId());
        resultMap.put(MARKER_ID_FIELD, null);
    }

    @Override
    public void onDeleteMarkerDone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handleIntent(getIntent());
            }
        });
    }

    @Override
    protected TrackRecordingServiceConnection getTrackRecordingServiceConnection() {
        return trackRecordingServiceConnection;
    }

    @Override
    protected void onDeleted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handleIntent(getIntent());
            }
        });
    }
}
