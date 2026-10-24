package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Country advertised by the configured VPN app, not IP geolocation. Unknown notification formats stay blank. */
final class VpnCountry {
    private static final Pattern CONNECTED = Pattern.compile(
            "^(?:(?:Connected to|Connecté à|Connectée à)\\s+)?(.+?)(?:\\s+#\\d+)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static String parse(String title, Locale displayLocale) {
        if (title == null || title.length() > 256) return "";
        Matcher match = CONNECTED.matcher(title.trim());
        if (!match.matches()) return "";
        String name = match.group(1).trim();
        for (String code : Locale.getISOCountries()) {
            Locale country = new Locale("", code);
            if (name.equalsIgnoreCase(country.getDisplayCountry(Locale.ENGLISH))
                    || name.equalsIgnoreCase(country.getDisplayCountry(Locale.FRENCH))
                    || name.equalsIgnoreCase(country.getDisplayCountry(displayLocale))) {
                return country.getDisplayCountry(displayLocale);
            }
        }
        return "";
    }

    static boolean visible(boolean configured, boolean activeOnly, boolean vpn) {
        return configured && (!activeOnly || vpn);
    }
}
