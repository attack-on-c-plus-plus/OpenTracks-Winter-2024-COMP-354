/*
 * Copyright 2010 Google Inc.
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
package de.dennisguse.opentracks.services.tasks;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;

import de.dennisguse.opentracks.content.data.Distance;
import de.dennisguse.opentracks.content.data.Track;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.stats.TrackStatistics;

/**
 * Execute a periodic task on a time or distance schedule.
 *
 * @author Sandor Dornbush
 */
public class VoiceAnnouncementManager {

    private static final String TAG = VoiceAnnouncementManager.class.getSimpleName();

    private final TrackRecordingService trackRecordingService;

    private VoiceAnnouncement voiceAnnouncement;

    private TrackStatistics trackStatistics;

    private static final Distance DISTANCE_OFF = Distance.of(Double.MAX_VALUE);
    private Distance distanceFrequency = DISTANCE_OFF;
    @NonNull
    private Distance nextTotalDistance = DISTANCE_OFF;

    private static final Duration TOTALTIME_OFF = Duration.ofMillis(Long.MAX_VALUE);
    private Duration totalTimeFrequency = TOTALTIME_OFF;
    @NonNull
    private Duration nextTotalTime = TOTALTIME_OFF;

    public VoiceAnnouncementManager(@NonNull TrackRecordingService trackRecordingService) {
        this.trackRecordingService = trackRecordingService;
    }

    public void restore(@Nullable TrackStatistics trackStatistics) {
        voiceAnnouncement = new VoiceAnnouncement(trackRecordingService);
        voiceAnnouncement.start();

        this.trackStatistics = trackStatistics;
        updateNextDuration();
        updateNextTaskDistance();
    }

    public void update(@NonNull Track track) {
        if (voiceAnnouncement == null) {
            Log.e(TAG, "Cannot update when in status shutdown.");
            return;
        }

        boolean announce = false;
        this.trackStatistics = track.getTrackStatistics();
        if (trackStatistics.getTotalDistance().greaterThan(nextTotalDistance)) {
            updateNextTaskDistance();
            announce = true;
        }
        if (!trackStatistics.getTotalTime().minus(nextTotalTime).isNegative()) {
            updateNextDuration();
            announce = true;
        }

        if (announce) {
            voiceAnnouncement.announce(track);
        }
    }

    public void shutdown() {
        if (voiceAnnouncement != null) {
            voiceAnnouncement.shutdown();
            voiceAnnouncement = null;
        }
    }

    public void setFrequency(Duration frequency) {
        this.totalTimeFrequency = frequency;
        restore(this.trackStatistics);
    }

    public void setFrequency(Distance frequency) {
        this.distanceFrequency = frequency;
        restore(this.trackStatistics);
    }

    public void updateNextTaskDistance() {
        if (trackStatistics == null || distanceFrequency.isZero()) {
            nextTotalDistance = DISTANCE_OFF;
        } else {

            Distance distance = trackStatistics.getTotalDistance();

            int index = (int) (distance.dividedBy(distanceFrequency));
            nextTotalDistance = distanceFrequency.multipliedBy(index + 1);
        }

    }

    private void updateNextDuration() {
        if (trackStatistics == null || totalTimeFrequency.isZero()) {
            nextTotalTime = TOTALTIME_OFF;
        } else {

            Duration totalTime = trackStatistics.getTotalTime();
            Duration intervalMod = Duration.ofMillis(trackStatistics.getTotalTime().toMillis() % totalTimeFrequency.toMillis());

            nextTotalTime = totalTime.plus(totalTimeFrequency.minus(intervalMod));
        }
    }

    @VisibleForTesting
    @NonNull
    public Duration getNextTotalTime() {
        return nextTotalTime;
    }

    @VisibleForTesting
    @NonNull
    public Distance getNextTotalDistance() {
        return nextTotalDistance;
    }
}
