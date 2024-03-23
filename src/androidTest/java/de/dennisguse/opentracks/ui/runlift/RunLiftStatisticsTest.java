package de.dennisguse.opentracks.ui.runlift;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.ui.intervals.IntervalStatisticsTest;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RunLiftStatisticsTest {

    private static final String TAG = RunLiftStatisticsTest.class.getSimpleName();

    private final Context context = ApplicationProvider.getApplicationContext();
    private ContentProviderUtils contentProviderUtils;

    @Before
    public void setUp() {
        contentProviderUtils = new ContentProviderUtils(context);
    }


}
