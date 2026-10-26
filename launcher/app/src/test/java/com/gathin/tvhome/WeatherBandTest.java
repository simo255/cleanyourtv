package com.gathin.tvhome;

import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherBandTest {
    private JSONObject day(String date, int low, int high) throws Exception {
        return new JSONObject().put("date", date).put("min", low).put("max", high).put("code", 3);
    }
    @Test public void savedForecastFollowsDatesAtMidnightInsteadOfArrayPositions() throws Exception {
        String saved = new JSONArray().put(day("2026-10-25", 8, 16)).put(day("2026-10-26", 7, 12)).toString();
        assertEquals(16, WeatherBand.day(saved, LocalDate.of(2026, 10, 25)).getInt("max"));
        assertEquals(12, WeatherBand.day(saved, LocalDate.of(2026, 10, 26)).getInt("max"));
        assertNull(WeatherBand.day(saved, LocalDate.of(2026, 10, 27)));
    }
    @Test public void missingMalformedAndImpossibleForecastsStayUnavailable() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 25);
        for (String saved : new String[]{null, "not json", "[]", new JSONArray().put(day(date.toString(), 20, 10)).toString(),
                new JSONArray().put(day(date.toString(), -101, 10)).toString(),
                new JSONArray().put(new JSONObject().put("date", date.toString()).put("code", 0)).toString()})
            assertNull(WeatherBand.day(saved, date));
    }
}
