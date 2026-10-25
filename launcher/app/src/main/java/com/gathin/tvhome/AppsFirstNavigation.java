package com.gathin.tvhome;

/** Vertical levels of the optional apps-first Home. */
final class AppsFirstNavigation {
    enum Page { HOME, RECOMMENDATIONS, APPS }
    static Page back(Page page) {
        return page == Page.APPS ? Page.RECOMMENDATIONS : Page.HOME;
    }
    // The adapter reserves a full row for favorites, including unfilled slots.
    static int firstRemaining(int columns, int appCount) {
        return appCount > columns ? columns : -1;
    }
    private AppsFirstNavigation() { }
}
