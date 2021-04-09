package de.dennisguse.opentracks.util;

import android.content.Context;

import androidx.annotation.Nullable;

import java.time.Duration;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.data.Distance;
import de.dennisguse.opentracks.content.data.Speed;
import de.dennisguse.opentracks.stats.TrackStatistics;
import de.dennisguse.opentracks.viewmodels.IntervalStatistics;

public class AnnouncementUtils {

    private AnnouncementUtils() {
    }

    public static String getAnnouncement(Context context, TrackStatistics trackStatistics, boolean isMetricUnits, boolean isReportSpeed, @Nullable IntervalStatistics.Interval currentInterval) {
        Distance distance = trackStatistics.getTotalDistance();
        Speed distancePerTime = trackStatistics.getAverageMovingSpeed();
        double currentDistancePerTime = currentInterval != null ? currentInterval.getSpeed_ms() * UnitConversions.MPS_TO_KMH : 0; //TODO Use Speed?

        if (distance.isZero()) {
            return context.getString(R.string.voice_total_distance_zero);
        }

        if (!isMetricUnits) {
            currentDistancePerTime *= UnitConversions.KM_TO_MI;
        }

        String rate;
        String currentRate;
        String currentRateMsg;
        if (isReportSpeed) {
            int speedId = isMetricUnits ? R.plurals.voiceSpeedKilometersPerHour : R.plurals.voiceSpeedMilesPerHour;
            double distanceInUnit = distancePerTime.to(isMetricUnits);
            rate = context.getResources().getQuantityString(speedId, getQuantityCount(distanceInUnit), distanceInUnit);

            currentRate = context.getResources().getQuantityString(speedId, getQuantityCount(currentDistancePerTime), currentDistancePerTime);
            currentRateMsg = context.getString(R.string.voice_speed_lap, currentRate);
        } else {
            double timePerDistance = distancePerTime.isZero() ? 0.0 : 1 / distancePerTime.to(isMetricUnits); //TODO PACE

            int paceId = isMetricUnits ? R.string.voice_pace_per_kilometer : R.string.voice_pace_per_mile;
            Duration time = Duration.ofMillis((long) (timePerDistance * UnitConversions.HR_TO_MIN * UnitConversions.MIN_TO_S * UnitConversions.S_TO_MS));
            rate = context.getString(paceId, getAnnounceTime(context, time));

            double currentTimePerDistance = currentDistancePerTime == 0 ? 0.0 : 1 / currentDistancePerTime; //TODO PACE
            Duration currentTime = Duration.ofMillis((long) (currentTimePerDistance * UnitConversions.HR_TO_MIN * UnitConversions.MIN_TO_S * UnitConversions.S_TO_MS));
            currentRate = context.getString(paceId, getAnnounceTime(context, currentTime));
            currentRateMsg = context.getString(R.string.voice_pace_lap, currentRate);
        }

        int totalDistanceId = isMetricUnits ? R.plurals.voiceTotalDistanceKilometers : R.plurals.voiceTotalDistanceMiles;
        double distanceInUnit = distance.to(isMetricUnits);
        String totalDistance = context.getResources().getQuantityString(totalDistanceId, getQuantityCount(distanceInUnit), distanceInUnit);

        currentRateMsg = currentInterval == null ? "" : " " + currentRateMsg;

        return context.getString(R.string.voice_template, totalDistance, getAnnounceTime(context, trackStatistics.getMovingTime()), rate) + currentRateMsg;
    }

    //TODO We might need to localize this using strings.xml if order is relevant.
    private static String getAnnounceTime(Context context, Duration duration) {
        String result = "";

        int hours = (int) (duration.toHours());
        int minutes = (int) (duration.toMinutes() % 60);
        int seconds = (int) (duration.getSeconds() % 60);

        if (hours != 0) {
            String hoursText = context.getResources()
                    .getQuantityString(R.plurals.voiceHours, hours, hours);
            result += hoursText + " ";
        }
        String minutesText = context.getResources()
                .getQuantityString(R.plurals.voiceMinutes, minutes, minutes);
        String secondsText = context.getResources()
                .getQuantityString(R.plurals.voiceSeconds, seconds, seconds);

        return result + minutesText + " " + secondsText;
    }

    static int getQuantityCount(double d) {
        return (int) d;
    }
}

