package com.example.detection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.detection.logic.AlertThresholds;

import org.junit.Test;

public class AlertThresholdsTest {

    @Test
    public void lpg_belowThreshold_notHigh() {
        assertFalse(AlertThresholds.isLpgHigh(179));
    }

    @Test
    public void lpg_atThreshold_notHigh() {
        // 剛好等於門檻不算超標，跟後端evaluateSafetyStatus用的 > 判斷保持一致
        assertFalse(AlertThresholds.isLpgHigh(180));
    }

    @Test
    public void lpg_aboveThreshold_isHigh() {
        assertTrue(AlertThresholds.isLpgHigh(181));
    }

    @Test
    public void co_belowThreshold_notHigh() {
        assertFalse(AlertThresholds.isCoHigh(149));
    }

    @Test
    public void co_atThreshold_notHigh() {
        assertFalse(AlertThresholds.isCoHigh(150));
    }

    @Test
    public void co_aboveThreshold_isHigh() {
        assertTrue(AlertThresholds.isCoHigh(151));
    }
}
