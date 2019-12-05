/*
 * Copyright 2008 Google Inc.
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

package de.dennisguse.opentracks.chart;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Scroller;

import androidx.core.view.GestureDetectorCompat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.MarkerDetailActivity;
import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.Waypoint;
import de.dennisguse.opentracks.stats.ExtremityMonitor;
import de.dennisguse.opentracks.util.IntentUtils;
import de.dennisguse.opentracks.util.MarkerUtils;
import de.dennisguse.opentracks.util.StringUtils;
import de.dennisguse.opentracks.util.UnitConversions;

/**
 * Visualization of the chart.
 * Provides support for zooming (via pinch), scrolling, flinging, and selecting shown markers (single touch).
 *
 * @author Sandor Dornbush
 * @author Leif Hendrik Wilden
 */
public class ChartView extends View {

    public static final float MEDIUM_TEXT_SIZE = 18f;
    public static final float SMALL_TEXT_SIZE = 12f;

    public static final int Y_AXIS_INTERVALS = 5;

    public static final int NUM_SERIES = 6;
    public static final int ELEVATION_SERIES = 0;
    public static final int SPEED_SERIES = 1;
    public static final int PACE_SERIES = 2;
    public static final int HEART_RATE_SERIES = 3;
    public static final int CADENCE_SERIES = 4;
    public static final int POWER_SERIES = 5;

    private static final int TARGET_X_AXIS_INTERVALS = 4;

    private static final int MIN_ZOOM_LEVEL = 1;
    private static final int MAX_ZOOM_LEVEL = 10;

    private static final NumberFormat X_NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final NumberFormat X_FRACTION_FORMAT = NumberFormat.getNumberInstance();
    private static final int BORDER = 8;
    private static final int SPACER = 4;
    private static final int Y_AXIS_OFFSET = 16;

    static {
        X_FRACTION_FORMAT.setMaximumFractionDigits(1);
        X_FRACTION_FORMAT.setMinimumFractionDigits(1);
    }

    private final ChartValueSeries[] series = new ChartValueSeries[NUM_SERIES];
    private final List<double[]> chartData = new ArrayList<>();
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final ExtremityMonitor xExtremityMonitor = new ExtremityMonitor();
    private final Paint axisPaint;
    private final Paint xAxisMarkerPaint;
    private final Paint gridPaint;
    private final Drawable pointer;
    private final int markerWidth;
    private final int markerHeight;
    private final Scroller scroller;
    private double maxX = 1.0;
    private int zoomLevel = 1;

    private int leftBorder = BORDER;
    private int topBorder = BORDER;
    private int bottomBorder = BORDER;
    private int rightBorder = BORDER;
    private int spacer = SPACER;
    private int yAxisOffset = Y_AXIS_OFFSET;

    private int width = 0;
    private int height = 0;
    private int effectiveWidth = 0;
    private int effectiveHeight = 0;

    private boolean chartByDistance;
    private boolean metricUnits = true;
    private boolean reportSpeed = true;
    private boolean showPointer = false;

    private GestureDetectorCompat detectorScrollFlingTab = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (Math.abs(distanceX) > 0) {
                int availableToScroll = effectiveWidth * (zoomLevel - 1) - getScrollX();
                if (availableToScroll > 0) {
                    scrollBy(Math.min(availableToScroll, (int) distanceX));
                }
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            fling((int) -velocityX);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            // Check if the y event is within markerHeight of the marker center
            if (Math.abs(event.getY() - topBorder - spacer - markerHeight / 2f) < markerHeight) {
                int minDistance = Integer.MAX_VALUE;
                Waypoint nearestWaypoint = null;
                synchronized (waypoints) {
                    for (Waypoint waypoint : waypoints) {
                        int distance = Math.abs(getX(getWaypointXValue(waypoint)) - (int) event.getX() - getScrollX());
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestWaypoint = waypoint;
                        }
                    }
                }
                if (nearestWaypoint != null && minDistance < markerWidth) {
                    Intent intent = IntentUtils.newIntent(getContext(), MarkerDetailActivity.class)
                            .putExtra(MarkerDetailActivity.EXTRA_MARKER_ID, nearestWaypoint.getId());
                    getContext().startActivity(intent);
                    return true;
                }
            }

            return false;
        }
    });

    private ScaleGestureDetector detectorZoom = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scaleFactor >= 1.1f) {
                zoomIn();
                return true;
            } else if (scaleFactor <= 0.9) {
                zoomOut();
                return true;
            }
            return false;
        }
    });

    public ChartView(Context context, boolean chartByDistance) {
        super(context);
        this.chartByDistance = chartByDistance;

        series[ELEVATION_SERIES] = new ChartValueSeries(context,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                new int[]{5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000},
                R.string.description_elevation_metric,
                R.string.description_elevation_imperial,
                R.color.chart_elevation_fill,
                R.color.chart_elevation_border);
        series[SPEED_SERIES] = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{1, 5, 10, 20, 50, 100},
                R.string.description_speed_metric,
                R.string.description_speed_imperial,
                R.color.chart_speed_fill,
                R.color.chart_speed_border);
        series[PACE_SERIES] = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{1, 2, 5, 10, 15, 20, 30, 60, 120},
                R.string.description_pace_metric,
                R.string.description_pace_imperial,
                R.color.chart_pace_fill,
                R.color.chart_pace_border);
        series[HEART_RATE_SERIES] = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{25, 50},
                R.string.description_sensor_heart_rate,
                R.string.description_sensor_heart_rate,
                R.color.chart_heart_rate_fill,
                R.color.chart_heart_rate_border);
        series[CADENCE_SERIES] = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{5, 10, 25, 50},
                R.string.description_sensor_cadence,
                R.string.description_sensor_cadence,
                R.color.chart_cadence_fill,
                R.color.chart_cadence_border);
        series[POWER_SERIES] = new ChartValueSeries(context,
                0,
                1000,
                new int[]{5, 50, 100, 200},
                R.string.description_sensor_power,
                R.string.description_sensor_power,
                R.color.chart_power_fill,
                R.color.chart_power_border);

        float scale = context.getResources().getDisplayMetrics().density;

        axisPaint = new Paint();
        axisPaint.setStyle(Style.STROKE);
        axisPaint.setColor(context.getResources().getColor(android.R.color.black));
        axisPaint.setAntiAlias(true);
        axisPaint.setTextSize(SMALL_TEXT_SIZE * scale);

        xAxisMarkerPaint = new Paint(axisPaint);
        xAxisMarkerPaint.setTextAlign(Align.CENTER);

        gridPaint = new Paint();
        gridPaint.setStyle(Style.STROKE);
        gridPaint.setColor(context.getResources().getColor(android.R.color.darker_gray));
        gridPaint.setAntiAlias(false);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{3, 2}, 0));

        Paint markerPaint = new Paint();
        markerPaint.setStyle(Style.STROKE);
        markerPaint.setColor(context.getResources().getColor(android.R.color.darker_gray));
        markerPaint.setAntiAlias(false);

        pointer = context.getResources().getDrawable(R.drawable.ic_logo_color_24dp);
        pointer.setBounds(0, 0, pointer.getIntrinsicWidth(), pointer.getIntrinsicHeight());

        Drawable waypointMarker = MarkerUtils.getDefaultPhoto(context);
        markerWidth = waypointMarker.getIntrinsicWidth();
        markerHeight = waypointMarker.getIntrinsicHeight();
        waypointMarker.setBounds(0, 0, markerWidth, markerHeight);

        scroller = new Scroller(context);
        setFocusable(true);
        setClickable(true);
        updateDimensions();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return true;
    }

    /**
     * Sets the enabled value for a chart value series.
     *
     * @param index the chart value series index
     */
    public void setChartValueSeriesEnabled(int index, boolean enabled) {
        series[index].setEnabled(enabled);
    }

    /**
     * Sets metric units.
     *
     * @param value true to use metric units
     */
    public void setMetricUnits(boolean value) {
        metricUnits = value;
    }

    /**
     * Sets report speed.
     *
     * @param value true to report speed
     */
    public void setReportSpeed(boolean value) {
        reportSpeed = value;
    }

    /**
     * Sets show pointer.
     *
     * @param value true to show pointer
     */
    public void setShowPointer(boolean value) {
        showPointer = value;
    }

    /**
     * Adds data points.
     *
     * @param dataPoints an array of data points to be added
     */
    public void addDataPoints(ArrayList<double[]> dataPoints) {
        synchronized (chartData) {
            chartData.addAll(dataPoints);
            for (double[] dataPoint : dataPoints) {
                xExtremityMonitor.update(dataPoint[0]);
                for (int j = 0; j < series.length; j++) {
                    if (!Double.isNaN(dataPoint[j + 1])) {
                        series[j].update(dataPoint[j + 1]);
                    }
                }
            }
            updateDimensions();
            updatePaths();
        }
    }

    /**
     * Clears all data.
     */
    public void reset() {
        synchronized (chartData) {
            chartData.clear();
            xExtremityMonitor.reset();
            zoomLevel = 1;
            updateDimensions();
        }
    }

    /**
     * Resets scroll. To be called on the UI thread.
     */
    public void resetScroll() {
        scrollTo(0, 0);
    }

    /**
     * Adds a waypoint.
     *
     * @param waypoint the waypoint
     */
    public void addWaypoint(Waypoint waypoint) {
        synchronized (waypoints) {
            waypoints.add(waypoint);
        }
    }

    /**
     * Clears the waypoints.
     */
    public void clearWaypoints() {
        synchronized (waypoints) {
            waypoints.clear();
        }
    }

    /**
     * Returns true if can zoom in.
     */
    public boolean canZoomIn() {
        return zoomLevel < MAX_ZOOM_LEVEL;
    }

    /**
     * Returns true if can zoom out.
     */
    public boolean canZoomOut() {
        return zoomLevel > MIN_ZOOM_LEVEL;
    }

    /**
     * Zooms in one level.
     */
    public void zoomIn() {
        if (canZoomIn()) {
            zoomLevel++;
            updatePaths();
            invalidate();
        }
    }

    /**
     * Zooms out one level.
     */
    public void zoomOut() {
        if (canZoomOut()) {
            zoomLevel--;
            scroller.abortAnimation();
            int scrollX = getScrollX();
            int maxWidth = effectiveWidth * (zoomLevel - 1);
            if (scrollX > maxWidth) {
                scrollX = maxWidth;
                scrollTo(scrollX, 0);
            }
            updatePaths();
            invalidate();
        }
    }

    /**
     * Initiates flinging.
     *
     * @param velocityX velocity of fling in pixels per second
     */
    public void fling(int velocityX) {
        int maxWidth = effectiveWidth * (zoomLevel - 1);
        scroller.fling(getScrollX(), 0, velocityX, 0, 0, maxWidth, 0, 0);
        invalidate();
    }

    /**
     * Scrolls the view horizontally by a given amount.
     *
     * @param deltaX the number of pixels to scroll
     */
    public void scrollBy(int deltaX) {
        int scrollX = getScrollX() + deltaX;
        if (scrollX < 0) {
            scrollX = 0;
        }
        int maxWidth = effectiveWidth * (zoomLevel - 1);
        if (scrollX > maxWidth) {
            scrollX = maxWidth;
        }
        scrollTo(scrollX, 0);
    }

    /**
     * Called by the parent to indicate that the mScrollX/Y values need to be
     * updated. Triggers a redraw during flinging.
     */
    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int x = scroller.getCurrX();
            scrollTo(x, 0);
            if (oldX != x) {
                onScrollChanged(x, 0, oldX, 0);
                postInvalidate();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean isZoom = detectorZoom.onTouchEvent(event);
        boolean isScrollTab = detectorScrollFlingTab.onTouchEvent(event);
        return isZoom || isScrollTab;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateEffectiveDimensionsIfChanged(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        synchronized (chartData) {

            canvas.save();

            canvas.drawColor(Color.WHITE);

            canvas.save();

            clipToGraphArea(canvas);
            drawDataSeries(canvas);
            drawGrid(canvas);

            canvas.restore();

            drawSeriesTitles(canvas);
            drawXAxis(canvas);
            drawYAxis(canvas);

            canvas.restore();

            if (showPointer) {
                drawPointer(canvas);
            }
        }
    }

    /**
     * Clips a canvas to the graph area.
     *
     * @param canvas the canvas
     */
    private void clipToGraphArea(Canvas canvas) {
        int x = getScrollX() + leftBorder;
        int y = topBorder;
        canvas.clipRect(x, y, x + effectiveWidth, y + effectiveHeight);
    }

    /**
     * Draws the data series.
     *
     * @param canvas the canvas
     */
    private void drawDataSeries(Canvas canvas) {
        for (ChartValueSeries chartValueSeries : series) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData()) {
                chartValueSeries.drawPath(canvas);
            }
        }
    }

    /**
     * Draws the grid.
     *
     * @param canvas the canvas
     */
    private void drawGrid(Canvas canvas) {
        // X axis grid
        List<Double> xAxisMarkerPositions = getXAxisMarkerPositions(getXAxisInterval());
        for (double position : xAxisMarkerPositions) {
            int x = getX(position);
            canvas.drawLine(x, topBorder, x, topBorder + effectiveHeight, gridPaint);
        }
        // Y axis grid
        float rightEdge = getX(maxX);
        for (int i = 0; i <= Y_AXIS_INTERVALS; i++) {
            double percentage = (double) i / Y_AXIS_INTERVALS;
            int range = effectiveHeight - 2 * yAxisOffset;
            int y = topBorder + yAxisOffset + (int) (percentage * range);
            canvas.drawLine(leftBorder, y, rightEdge, y, gridPaint);
        }
    }

    /**
     * Draws series titles.
     *
     * @param canvas the canvas
     */
    private void drawSeriesTitles(Canvas canvas) {
        int[] titleDimensions = getTitleDimensions();
        int lines = titleDimensions[0];
        int lineHeight = titleDimensions[1];
        int count = 0;
        for (int i = 0; i < series.length; i++) {
            ChartValueSeries chartValueSeries = series[i];
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(i)) {
                count++;
                String title = getContext().getString(chartValueSeries.getTitleId(metricUnits));
                Paint paint = chartValueSeries.getTitlePaint();
                int x = (int) (0.5 * width) + getScrollX();
                int y = topBorder - spacer - (lines - count) * (lineHeight + spacer);
                canvas.drawText(title, x, y, paint);
            }
        }
    }

    /**
     * Gets the title dimensions.
     * Returns an array of 2 integers, first element is the number of lines and the second element is the line height.
     */
    private int[] getTitleDimensions() {
        int lines = 0;
        int lineHeight = 0;
        for (int i = 0; i < series.length; i++) {
            ChartValueSeries chartValueSeries = series[i];
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(i)) {
                lines++;
                String title = getContext().getString(chartValueSeries.getTitleId(metricUnits));
                Rect rect = getRect(chartValueSeries.getTitlePaint(), title);
                if (rect.height() > lineHeight) {
                    lineHeight = rect.height();
                }
            }
        }
        return new int[]{lines, lineHeight};
    }

    /**
     * Draws the x axis.
     *
     * @param canvas the canvas
     */
    private void drawXAxis(Canvas canvas) {
        int x = getScrollX() + leftBorder;
        int y = topBorder + effectiveHeight;
        canvas.drawLine(x, y, x + effectiveWidth, y, axisPaint);
        String label = getXAxisLabel();
        Rect rect = getRect(axisPaint, label);
        int yOffset = rect.height() / 2;
        canvas.drawText(label, x + effectiveWidth + spacer, y + yOffset, axisPaint);

        double interval = getXAxisInterval();
        List<Double> markerPositions = getXAxisMarkerPositions(interval);
        NumberFormat numberFormat = interval < 1 ? X_FRACTION_FORMAT : X_NUMBER_FORMAT;
        for (int i = 0; i < markerPositions.size(); i++) {
            drawXAxisMarker(canvas, markerPositions.get(i), numberFormat, spacer + yOffset);
        }
    }

    /**
     * Gets the x axis label.
     */
    private String getXAxisLabel() {
        Context context = getContext();
        if (chartByDistance) {
            return metricUnits ? context.getString(R.string.unit_kilometer) : context.getString(R.string.unit_mile);
        } else {
            return context.getString(R.string.description_time);
        }
    }

    /**
     * Draws a x axis marker.
     *
     * @param canvas       canvas
     * @param value        value
     * @param numberFormat the number format
     * @param spacing      the spacing between x axis and marker
     */
    private void drawXAxisMarker(Canvas canvas, double value, NumberFormat numberFormat, int spacing) {
        String marker = chartByDistance ? numberFormat.format(value) : StringUtils.formatElapsedTime((long) value);
        Rect rect = getRect(xAxisMarkerPaint, marker);
        canvas.drawText(marker, getX(value), topBorder + effectiveHeight + spacing + rect.height(), xAxisMarkerPaint);
    }

    /**
     * Gets the x axis interval.
     */
    private double getXAxisInterval() {
        double interval = maxX / zoomLevel / TARGET_X_AXIS_INTERVALS;
        if (interval < 1) {
            interval = .5;
        } else if (interval < 5) {
            interval = 2;
        } else if (interval < 10) {
            interval = 5;
        } else {
            interval = (interval / 10) * 10;
        }
        return interval;
    }

    /**
     * Gets the x axis marker positions.
     */
    private List<Double> getXAxisMarkerPositions(double interval) {
        List<Double> markers = new ArrayList<>();
        markers.add(0d);
        for (int i = 1; i * interval < maxX; i++) {
            markers.add(i * interval);
        }
        // At least 2 markers
        if (markers.size() < 2) {
            markers.add(maxX);
        }
        return markers;
    }

    /**
     * Draws the y axis.
     *
     * @param canvas the canvas
     */
    private void drawYAxis(Canvas canvas) {
        int x = getScrollX() + leftBorder;
        int y = topBorder;
        canvas.drawLine(x, y, x, y + effectiveHeight, axisPaint);

        int markerXPosition = x - spacer;
        for (int i = 0; i < series.length; i++) {
            int index = series.length - 1 - i;
            ChartValueSeries chartValueSeries = series[index];
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(index)) {
                markerXPosition -= drawYAxisMarkers(chartValueSeries, canvas, markerXPosition) + spacer;
            }
        }
    }

    /**
     * Draws the y axis markers for a chart value series.
     *
     * @param chartValueSeries the chart value series
     * @param canvas           the canvas
     * @param xPosition        the right most x position
     * @return the maximum marker width.
     */
    private float drawYAxisMarkers(ChartValueSeries chartValueSeries, Canvas canvas, int xPosition) {
        int interval = chartValueSeries.getInterval();
        float maxMarkerWidth = 0;
        for (int i = 0; i <= Y_AXIS_INTERVALS; i++) {
            maxMarkerWidth = Math.max(maxMarkerWidth, drawYAxisMarker(chartValueSeries, canvas, xPosition,
                    i * interval + chartValueSeries.getMinMarkerValue()));
        }
        return maxMarkerWidth;
    }

    /**
     * Draws a y axis marker.
     *
     * @param chartValueSeries the chart value series
     * @param canvas           the canvas
     * @param xPosition        the right most x position
     * @param yValue           the y value
     * @return the marker width.
     */
    private float drawYAxisMarker(ChartValueSeries chartValueSeries, Canvas canvas, int xPosition, int yValue) {
        String marker = chartValueSeries.formatMarker(yValue);
        Paint paint = chartValueSeries.getMarkerPaint();
        Rect rect = getRect(paint, marker);
        int yPosition = getY(chartValueSeries, yValue) + (rect.height() / 2);
        canvas.drawText(marker, xPosition, yPosition, paint);
        return paint.measureText(marker);
    }

    /**
     * Draws the current pointer.
     *
     * @param canvas the canvas
     */
    private void drawPointer(Canvas canvas) {
        int index = -1;
        for (int i = 0; i < series.length; i++) {
            ChartValueSeries chartValueSeries = series[i];
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData()) {
                index = i;
                break;
            }
        }
        if (index != -1 && chartData.size() > 0) {
            int dx = getX(maxX) - pointer.getIntrinsicWidth() / 2;
            int dy = getY(series[index], chartData.get(chartData.size() - 1)[index + 1])
                    - pointer.getIntrinsicHeight();
            canvas.translate(dx, dy);
            pointer.draw(canvas);
        }
    }

    /**
     * Updates paths. The path needs to be updated any time after the data or the dimensions change.
     */
    private void updatePaths() {
        synchronized (chartData) {
            for (ChartValueSeries chartValueSeries : series) {
                chartValueSeries.getPath().reset();
            }
            drawPaths();
            closePaths();
        }
    }

    /**
     * Draws all paths.
     */
    private void drawPaths() {
        boolean[] hasMoved = new boolean[series.length];

        for (double[] dataPoint : chartData) {
            for (int j = 0; j < series.length; j++) {
                double value = dataPoint[j + 1];
                if (Double.isNaN(value)) {
                    continue;
                }
                ChartValueSeries chartValueSeries = series[j];
                Path path = chartValueSeries.getPath();
                int x = getX(dataPoint[0]);
                int y = getY(chartValueSeries, value);
                if (!hasMoved[j]) {
                    hasMoved[j] = true;
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
        }
    }

    /**
     * Closes all paths.
     */
    private void closePaths() {
        for (int i = 0; i < series.length; i++) {
            int first = getFirstPopulatedChartDataIndex(i);

            if (first != -1) {
                int xCorner = getX(chartData.get(first)[0]);
                int yCorner = topBorder + effectiveHeight;
                ChartValueSeries chartValueSeries = series[i];
                Path path = chartValueSeries.getPath();
                // Bottom right corner
                path.lineTo(getX(chartData.get(chartData.size() - 1)[0]), yCorner);
                // Bottom left corner
                path.lineTo(xCorner, yCorner);
                // Top right corner
                path.lineTo(xCorner, getY(chartValueSeries, chartData.get(first)[i + 1]));
            }
        }
    }

    /**
     * Finds the index of the first data point containing data for a series.
     * Returns -1 if no data point contains data for the series.
     *
     * @param seriesIndex the series's index
     */
    private int getFirstPopulatedChartDataIndex(int seriesIndex) {
        for (int i = 0; i < chartData.size(); i++) {
            if (!Double.isNaN(chartData.get(i)[seriesIndex + 1])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Updates the chart dimensions.
     */
    private void updateDimensions() {
        maxX = xExtremityMonitor.hasData() ? xExtremityMonitor.getMax() : 1.0;
        for (ChartValueSeries chartValueSeries : series) {
            chartValueSeries.updateDimension();
        }
        float density = getContext().getResources().getDisplayMetrics().density;
        spacer = (int) (density * SPACER);
        yAxisOffset = (int) (density * Y_AXIS_OFFSET);

        int markerLength = 0;
        for (int i = 0; i < series.length; i++) {
            ChartValueSeries chartValueSeries = series[i];
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(i)) {
                Rect rect = getRect(chartValueSeries.getMarkerPaint(), chartValueSeries.getLargestMarker());
                markerLength += rect.width() + spacer;
            }
        }

        leftBorder = (int) (density * BORDER + markerLength);
        int[] titleDimensions = getTitleDimensions();
        topBorder = (int) (density * BORDER + titleDimensions[0] * (titleDimensions[1] + spacer));
        Rect xAxisLabelRect = getRect(axisPaint, getXAxisLabel());
        // border + x axis marker + spacer + .5 x axis label
        bottomBorder = (int) (density * BORDER + getRect(xAxisMarkerPaint, "1").height() + spacer + (xAxisLabelRect.height() / 2));
        rightBorder = (int) (density * BORDER + xAxisLabelRect.width() + spacer);
        updateEffectiveDimensions();
    }

    /**
     * Updates the effective dimensions.
     */
    private void updateEffectiveDimensions() {
        effectiveWidth = Math.max(0, width - leftBorder - rightBorder);
        effectiveHeight = Math.max(0, height - topBorder - bottomBorder);
    }

    /**
     * Updates the effective dimensions if changed.
     *
     * @param newWidth  the new width
     * @param newHeight the new height
     */
    private void updateEffectiveDimensionsIfChanged(int newWidth, int newHeight) {
        if (width != newWidth || height != newHeight) {
            width = newWidth;
            height = newHeight;
            updateEffectiveDimensions();
            updatePaths();
        }
    }

    /**
     * Gets the x position for a value.
     *
     * @param value the value
     */
    private int getX(double value) {
        if (value > maxX) {
            value = maxX;
        }
        double percentage = value / maxX;
        return leftBorder + (int) (percentage * effectiveWidth * zoomLevel);
    }

    /**
     * Gets the y position for a value in a chart value series
     *
     * @param chartValueSeries the chart value series
     * @param value            the value
     */
    private int getY(ChartValueSeries chartValueSeries, double value) {
        int effectiveSpread = chartValueSeries.getInterval() * Y_AXIS_INTERVALS;
        double percentage = (value - chartValueSeries.getMinMarkerValue()) / effectiveSpread;
        int rangeHeight = effectiveHeight - 2 * yAxisOffset;
        return topBorder + yAxisOffset + (int) ((1 - percentage) * rangeHeight);
    }

    /**
     * Gets a waypoint's x value.
     *
     * @param waypoint the waypoint
     */
    private double getWaypointXValue(Waypoint waypoint) {
        if (chartByDistance) {
            double lenghtInKm = waypoint.getLength() * UnitConversions.M_TO_KM;
            return metricUnits ? lenghtInKm : lenghtInKm * UnitConversions.KM_TO_MI;
        } else {
            return waypoint.getDuration();
        }
    }

    /**
     * Gets a paint's Rect for a string.
     *
     * @param paint  the paint
     * @param string the string
     */
    private Rect getRect(Paint paint, String string) {
        Rect rect = new Rect();
        paint.getTextBounds(string, 0, string.length(), rect);
        return rect;
    }

    /**
     * Returns true if the index is allowed when the chartData is empty.
     *
     * @param index the index
     */
    private boolean allowIfEmpty(int index) {
        if (!chartData.isEmpty()) {
            return false;
        }
        switch (index) {
            case ELEVATION_SERIES:
                return true;
            case SPEED_SERIES:
                return reportSpeed;
            case PACE_SERIES:
                return !reportSpeed;
            default:
                return false;
        }
    }
}
