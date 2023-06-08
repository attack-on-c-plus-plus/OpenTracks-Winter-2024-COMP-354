package de.dennisguse.opentracks.sensors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import de.dennisguse.opentracks.data.models.AtmosphericPressure;

public class PressureSensorUtilsTest {

    // real data at 500Hz stationary
    @Test
    public void altitudeChanges_none() {
        // given
        float[] sensorValues_hPa = new float[]{1015.6876f, 1015.699f, 1015.70905f, 1015.71075f, 1015.7224f, 1015.72f, 1015.7373f, 1015.7481f, 1015.74133f, 1015.73553f, 1015.7462f, 1015.74896f, 1015.752f, 1015.754f, 1015.7456f, 1015.73303f, 1015.7288f, 1015.7239f, 1015.7298f, 1015.7387f, 1015.72534f, 1015.72577f, 1015.72797f, 1015.71796f, 1015.7113f, 1015.7097f, 1015.7096f, 1015.7013f, 1015.7072f, 1015.71063f, 1015.7088f, 1015.7115f, 1015.713f, 1015.7121f, 1015.7062f, 1015.7062f, 1015.6994f, 1015.69617f, 1015.68945f, 1015.698f, 1015.6913f, 1015.69696f, 1015.69293f, 1015.6955f, 1015.6928f, 1015.6939f, 1015.68787f, 1015.6881f, 1015.68536f, 1015.68726f, 1015.6879f, 1015.6904f, 1015.6937f, 1015.69476f, 1015.7046f, 1015.7019f, 1015.7079f, 1015.71124f, 1015.71216f, 1015.71436f, 1015.7171f, 1015.7186f, 1015.7186f, 1015.7246f, 1015.72046f, 1015.7211f, 1015.72375f, 1015.7194f, 1015.72125f, 1015.7161f, 1015.71185f, 1015.7188f, 1015.721f, 1015.7252f, 1015.72266f, 1015.7236f, 1015.72614f, 1015.7266f, 1015.7344f, 1015.7376f, 1015.7337f, 1015.7253f, 1015.71875f, 1015.7219f, 1015.7219f, 1015.7317f, 1015.7284f, 1015.73444f, 1015.73584f, 1015.7369f, 1015.73254f, 1015.7369f, 1015.7336f, 1015.73254f, 1015.73676f, 1015.74176f, 1015.74506f, 1015.7478f, 1015.755f, 1015.7515f, 1015.7543f, 1015.7459f, 1015.7468f, 1015.7492f, 1015.74585f, 1015.74756f, 1015.74756f, 1015.75085f, 1015.754f, 1015.75336f, 1015.7568f, 1015.75165f, 1015.761f, 1015.7527f, 1015.75684f, 1015.7635f, 1015.7585f, 1015.7552f, 1015.7475f, 1015.74506f, 1015.7542f, 1015.75586f, 1015.74176f, 1015.74414f, 1015.7501f, 1015.751f, 1015.7478f, 1015.755f, 1015.75836f, 1015.7592f, 1015.75256f, 1015.7559f, 1015.76654f, 1015.76154f, 1015.766f, 1015.77014f, 1015.76904f, 1015.76013f, 1015.7674f, 1015.76514f, 1015.7674f, 1015.7635f, 1015.76404f, 1015.7607f, 1015.754f, 1015.7574f, 1015.7607f, 1015.7607f, 1015.7607f, 1015.754f, 1015.7567f, 1015.7616f, 1015.7643f, 1015.7666f, 1015.7609f, 1015.766f, 1015.76764f, 1015.755f, 1015.75757f, 1015.74927f, 1015.74927f, 1015.7426f, 1015.745f, 1015.745f, 1015.7409f, 1015.7376f, 1015.7326f, 1015.74164f, 1015.74664f, 1015.745f, 1015.7459f, 1015.7502f, 1015.7493f, 1015.7476f, 1015.7493f, 1015.751f, 1015.7601f, 1015.76434f, 1015.766f, 1015.756f, 1015.7617f, 1015.7623f, 1015.76404f, 1015.76733f, 1015.7607f, 1015.7623f, 1015.76337f, 1015.7584f, 1015.7573f, 1015.7502f, 1015.7534f, 1015.7534f, 1015.7601f, 1015.75684f, 1015.7518f, 1015.75574f, 1015.7524f, 1015.7507f, 1015.7484f, 1015.7502f, 1015.7534f, 1015.7623f, 1015.75903f, 1015.75574f, 1015.75574f, 1015.7573f, 1015.7524f, 1015.75574f, 1015.75574f, 1015.7607f, 1015.7573f, 1015.754f, 1015.74677f, 1015.754f, 1015.75574f, 1015.75903f, 1015.7573f, 1015.7551f, 1015.7573f, 1015.754f, 1015.7507f, 1015.7374f, 1015.739f, 1015.73566f, 1015.73566f, 1015.7367f, 1015.7417f, 1015.7417f, 1015.739f, 1015.74835f, 1015.74567f, 1015.7517f, 1015.74066f, 1015.7374f, 1015.739f, 1015.7417f, 1015.73065f, 1015.72894f, 1015.73505f, 1015.73334f, 1015.74005f, 1015.74005f, 1015.7367f, 1015.7434f, 1015.744f, 1015.7423f, 1015.7384f, 1015.739f, 1015.7374f, 1015.7374f, 1015.7374f, 1015.7341f, 1015.73505f, 1015.7341f, 1015.7267f, 1015.72833f, 1015.7234f, 1015.7167f, 1015.725f, 1015.7217f, 1015.7234f, 1015.73004f, 1015.7324f, 1015.7423f, 1015.74005f, 1015.73505f, 1015.73004f, 1015.739f, 1015.7417f, 1015.74567f, 1015.74066f, 1015.74146f, 1015.74146f, 1015.73816f, 1015.7274f, 1015.72906f, 1015.7274f, 1015.7341f, 1015.739f, 1015.744f, 1015.7417f, 1015.7374f, 1015.7324f, 1015.73816f, 1015.74146f, 1015.7374f, 1015.7341f, 1015.7365f, 1015.7365f, 1015.74146f, 1015.74414f, 1015.7475f, 1015.7448f, 1015.74146f, 1015.74585f, 1015.7475f, 1015.75146f, 1015.75146f, 1015.7591f, 1015.75476f, 1015.7614f, 1015.75476f, 1015.7492f, 1015.74817f, 1015.7498f, 1015.7515f, 1015.75586f, 1015.7509f, 1015.7448f, 1015.7525f, 1015.7581f, 1015.7592f};
        AtmosphericPressure firstSensorValue = AtmosphericPressure.ofHPA(sensorValues_hPa[0]);

        // when // then
        for (float v : sensorValues_hPa) {
            assertNull(PressureSensorUtils.computeChanges(firstSensorValue, AtmosphericPressure.ofHPA(v)));
        }
    }

    // Simulate a sudden drop of altitude.
    // test data
    @Test
    public void computeChanges_downhill() {
        float[] sensorValues_hPa = new float[]{1015f, 1015.01f, 1015.02f, 1015.03f, 1015.04f, 1015.05f, 1015.06f, 1015.07f, 1015.08f, 1015.09f, 1015.10f, 1015.11f, 1015.12f, 1015.13f, 1018f, 1018.1f, 1018.1f, 1018.1f, 1018.1f};
        AtmosphericPressure firstSensorValue = AtmosphericPressure.ofHPA(sensorValues_hPa[0]);

        // when
        float altitudeGain_m = 0;
        float altitudeLoss = 0;
        AtmosphericPressure lastUsedPressureValue = firstSensorValue;

        for (float v : sensorValues_hPa) {
            PressureSensorUtils.AltitudeChange altitudeChange = PressureSensorUtils.computeChanges(lastUsedPressureValue, AtmosphericPressure.ofHPA(v));
            if (altitudeChange != null) {
                altitudeGain_m += altitudeChange.getAltitudeGain_m();
                altitudeLoss += altitudeChange.getAltitudeLoss_m();
                lastUsedPressureValue = altitudeChange.currentSensorValue();
            }
        }

        // then
        assertEquals(0f, altitudeGain_m, 0.01);
        assertEquals(15.0f, altitudeLoss, 0.01);
        assertEquals(1016.80f, lastUsedPressureValue.getHPA(), 0.01); //Expect exponential smoothing
    }
}