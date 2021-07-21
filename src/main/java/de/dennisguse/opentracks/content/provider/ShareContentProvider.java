package de.dennisguse.opentracks.content.provider;

import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.dennisguse.opentracks.content.data.Track;
import de.dennisguse.opentracks.content.data.TracksColumns;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;

/**
 * A content provider that mimics the behavior of {@link androidx.core.content.FileProvider}, which shares virtual (non-existing) KML-files.
 * The actual content of the virtual files is generated by using the functionality defined in {@link CustomContentProvider}.
 * <p>
 * Moreover, it manages access to OpenTrack's database via {@link CustomContentProvider}.
 * <p>
 * Explanation:
 * Although a request is handled by a {@link android.content.ContentProvider} (with temporarily granted permission), Android's security infrastructure prevents forwarding queries to non-exported {@link android.content.ContentProvider}.
 * Thus, if {@link ShareContentProvider} and {@link CustomContentProvider} would be two different instances, the data would not be accessible to external apps.
 * While handling a request {@link ShareContentProvider} could `grantPermissions()` to the calling app for {@link CustomContentProvider}'s URI.
 * However, while handling the request this would allow the calling app to actually contact {@link CustomContentProvider} directly and get access to stored data that should remain private.
 */
public class ShareContentProvider extends CustomContentProvider {

    private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    private static final String TAG = ShareContentProvider.class.getSimpleName();

    private static final int URI_GPX = 0;
    private static final int URI_KML_WITH_TRACKDETAIL_SENSORDATA = 3;

    private static final int URI_KMZ_WITH_TRACKDETAIL_AND_SENSORDATA = 6;
    private static final int URI_KMZ_WITH_TRACKDETAIL_SENSORDATA_AND_PICTURES = 7;

    private static final int URI_SHARE_PICTURE = 8;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final String TRACKID_DELIMITER = "_";

    static {
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.GPX.getName() + "/*/*", URI_GPX);

        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA.getName() + "/*/*", URI_KML_WITH_TRACKDETAIL_SENSORDATA);

        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA.getName() + "/*/*", URI_KMZ_WITH_TRACKDETAIL_AND_SENSORDATA);
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES.getName() + "/*/*", URI_KMZ_WITH_TRACKDETAIL_SENSORDATA_AND_PICTURES);

        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.SHARE_PICTURE_PNG.getName() + "/*/*", URI_SHARE_PICTURE);
    }

    /**
     * @return An URI for one file containing this tracks.
     */
    public static Pair<Uri, String> createURI(Track.Id trackId, String trackName, @NonNull TrackFileFormat trackFileFormat) {
        Set<Track.Id> trackIds = new HashSet<>(1);
        trackIds.add(trackId);
        return createURI(trackIds, trackName, trackFileFormat);
    }

    /**
     * @return An URI for one file containing all tracks.
     */
    public static Pair<Uri, String> createURI(Set<Track.Id> trackIds, String trackName, @NonNull TrackFileFormat trackFileFormat) {
        if (trackIds.isEmpty()) {
            throw new UnsupportedOperationException();
        }

        StringBuilder trackIdBuilder = new StringBuilder();
        for (Track.Id trackId : trackIds) {
            trackIdBuilder.append(trackId.getId()).append(TRACKID_DELIMITER);
        }
        trackIdBuilder.deleteCharAt(trackIdBuilder.lastIndexOf(TRACKID_DELIMITER));

        Uri uri = Uri.parse(TracksColumns.CONTENT_URI + "/" + trackFileFormat.getName() + "/" + trackIdBuilder + "/" + Uri.encode(trackName) + "." + trackFileFormat.getExtension());
        String mime = getTypeMime(uri);

        Log.d(TAG, "Created uri " + uri.toString() + " with MIME " + mime);

        return new Pair<>(uri, mime);
    }

    static Set<Track.Id> parseURI(Uri uri) {
        List<String> uriPaths = uri.getPathSegments();
        if (uriPaths == null || uriPaths.size() < 3) {
            Log.d(TAG, "URI does not contain any trackIds.");
            return new HashSet<>();
        }

        String[] uriTrackIds = uriPaths.get(2).split(TRACKID_DELIMITER);

        Set<Track.Id> trackIds = new HashSet<>();
        for (String uriTrackId : uriTrackIds) {
            trackIds.add(new Track.Id(Long.parseLong(uriTrackId)));
        }
        return trackIds;
    }

    /**
     * Do not allow to be exported via AndroidManifest.
     * Check that caller has permissions to access {@link CustomContentProvider}.
     */
    @Override
    public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
        super.attachInfo(context, info);

        // Sanity check our security
        if (info.exported) {
            throw new UnsupportedOperationException("Provider must not be exported");
        }

        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    private static TrackFileFormat getTrackFileFormat(@NonNull Uri uri) {
        switch (uriMatcher.match(uri)) {
            case URI_GPX:
                return TrackFileFormat.GPX;

            case URI_KML_WITH_TRACKDETAIL_SENSORDATA:
                return TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA;

            case URI_KMZ_WITH_TRACKDETAIL_AND_SENSORDATA:
                return TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA;
            case URI_KMZ_WITH_TRACKDETAIL_SENSORDATA_AND_PICTURES:
                return TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES;

            case URI_SHARE_PICTURE:
                return TrackFileFormat.SHARE_PICTURE_PNG;

            default:
                throw new RuntimeException("Could not derive TrackFileFormat from Uri " + uri);
        }
    }

    @Nullable
    private static String getTypeMime(@NonNull Uri uri) {
        return getTrackFileFormat(uri).getMimeType();
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (uriMatcher.match(uri) == -1) {
            return super.query(uri, projection, selection, selectionArgs, sortOrder);
        }

        // ContentProvider has already checked granted permissions
        if (projection == null) {
            projection = COLUMNS;
        }

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            switch (col) {
                case OpenableColumns.DISPLAY_NAME:
                    cols[i] = OpenableColumns.DISPLAY_NAME;
                    values[i++] = uri.getLastPathSegment();
                    break;
                case OpenableColumns.SIZE:
                    cols[i] = OpenableColumns.SIZE;
                    values[i++] = -1; //Report unknown size; if applications need to know, one need to generate the file here also (count bytes that are written to OutputStream.
                    break;
            }
        }

        cols = Arrays.copyOf(cols, i);
        values = Arrays.copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        String mime = getTypeMime(uri);
        if (mime != null) {
            return mime;
        }

        return super.getType(uri);
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Set<Track.Id> trackIds = parseURI(uri);
        final ArrayList<Track> tracks = new ArrayList<>();
        String[] trackIdsString = trackIds.stream().map(Track.Id::toString).toArray(String[]::new);
        String whereClause = String.format(TracksColumns._ID + " IN (%s)", TextUtils.join(",", Collections.nCopies(trackIds.size(), "?")));

        try (Cursor cursor = super.query(TracksColumns.CONTENT_URI, null, whereClause, trackIdsString, TracksColumns._ID)) {
            while (cursor.moveToNext()) {
                tracks.add(ContentProviderUtils.createTrack(cursor));
            }
        }

        final TrackExporter trackExporter = getTrackFileFormat(uri).createTrackExporter(getContext());

        PipeDataWriter<String> pipeDataWriter = (output, uri1, mimeType, opts, args) -> {
            try (FileOutputStream fileOutputStream = new FileOutputStream(output.getFileDescriptor())) {
                // TODO handle failure (i.e., do not export an empty file)
                trackExporter.writeTrack(tracks.toArray(new Track[0]), fileOutputStream);
            } catch (IOException e) {
                Log.w(TAG, "there occurred an error while sharing a file: " + e);
            }
        };

        return openPipeHelper(uri, getType(uri), null, null, pipeDataWriter);
    }
}
