package com.example.detection;

import static org.junit.Assert.assertEquals;

import com.example.detection.logic.HistoryUrlBuilder;

import org.junit.Test;

public class HistoryUrlBuilderTest {

    private static final String BASE = "http://10.0.2.2:3000/api/sensors/history";

    @Test
    public void noDates_returnsBaseUrlOnly() {
        assertEquals(BASE, HistoryUrlBuilder.build(BASE, null, null));
        assertEquals(BASE, HistoryUrlBuilder.build(BASE, "", ""));
    }

    @Test
    public void onlyStartDate_usesQuestionMark() {
        assertEquals(BASE + "?start=2026-01-01", HistoryUrlBuilder.build(BASE, "2026-01-01", null));
    }

    @Test
    public void onlyEndDate_usesQuestionMark() {
        assertEquals(BASE + "?end=2026-01-31", HistoryUrlBuilder.build(BASE, null, "2026-01-31"));
    }

    @Test
    public void bothDates_secondParamUsesAmpersand() {
        assertEquals(
                BASE + "?start=2026-01-01&end=2026-01-31",
                HistoryUrlBuilder.build(BASE, "2026-01-01", "2026-01-31")
        );
    }

    @Test
    public void datesWithWhitespace_areTrimmed() {
        assertEquals(
                BASE + "?start=2026-01-01&end=2026-01-31",
                HistoryUrlBuilder.build(BASE, "  2026-01-01  ", "  2026-01-31  ")
        );
    }
}
