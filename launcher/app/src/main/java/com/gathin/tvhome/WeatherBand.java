package com.gathin.tvhome;

import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

/** Select by calendar date so a saved forecast never calls yesterday "today". */
final class WeatherBand {
    private WeatherBand() { }
    static JSONObject day(String forecast, LocalDate date) {
        if (forecast == null || forecast.length() > 16_000) return null;
        try {
            JSONArray days = new JSONArray(forecast);
            for (int i = 0; i < Math.min(days.length(), 8); i++) {
                JSONObject day = days.optJSONObject(i);
                if (day == null || !date.toString().equals(day.optString("date"))) continue;
                int max = day.optInt("max", 999), min = day.optInt("min", 999);
                if (min >= -100 && max <= 70 && min <= max && day.has("code")) return day;
            }
        } catch (org.json.JSONException ignored) { }
        return null;
    }
    static String icon(int code) {
        if (code >= 95) return "ϟ";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄\uFE0E";
        if (code >= 51 && code <= 82) return "☂\uFE0E";
        if (code <= 1 && code >= 0) return "☀\uFE0E";
        return "☁\uFE0E";
    }
}
