package com.gathin.tvhome;
final class PrivateFeedCheck {
    private PrivateFeedCheck() { }
    static boolean keepPreviousCache(int historyCount, int catalogCount, boolean knownHistory) {
        // An invalid add-on ID returns 200 with everything empty; cleared history with recommendations is valid.
        return knownHistory && historyCount == 0 && catalogCount == 0;
    }
}
