package com.gathin.tvhome;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class VpnCountryTest {
    @Test public void readsObservedFrenchNotificationWithEnglishCountry() {
        assertEquals("Albanie", VpnCountry.parse("Connecté à Albania #170", Locale.FRENCH));
    }

    @Test public void readsEnglishAndLocalizedCountries() {
        assertEquals("États-Unis", VpnCountry.parse("Connected to United States #1234", Locale.FRENCH));
        assertEquals("France", VpnCountry.parse("Connecté à France #100", Locale.ENGLISH));
        assertEquals("Germany", VpnCountry.parse("Connecté à Allemagne #100", Locale.ENGLISH));
        assertEquals("Canada", VpnCountry.parse("Canada", Locale.FRENCH));
        assertEquals("France", VpnCountry.parse("France #100", Locale.ENGLISH));
    }

    @Test public void disconnectedConnectingAndMarketingNeverBecomeLocations() {
        for (String title : new String[]{null, "Disconnected", "Connecting to Albania #170", "VPN client", "Offer for Canada #123", "Connected to Unknown #123"}) {
            assertEquals("", VpnCountry.parse(title, Locale.FRENCH));
        }
    }

    @Test public void onlyShowsActiveVpnWhenRequestedAndRespectsHiddenPill() {
        assertFalse(VpnCountry.visible(true, true, false));
        assertTrue(VpnCountry.visible(true, true, true));
        assertFalse(VpnCountry.visible(false, true, true));
        assertTrue(VpnCountry.visible(true, false, false));
    }
}
