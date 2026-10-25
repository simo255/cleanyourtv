package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppsFirstNavigationTest {
    @Test public void remainingAppsSkipEveryFavoriteSlot() {
        assertEquals(-1, AppsFirstNavigation.firstRemaining(7, 0));
        assertEquals(-1, AppsFirstNavigation.firstRemaining(7, 5));
        assertEquals(-1, AppsFirstNavigation.firstRemaining(7, 7));
        assertEquals(7, AppsFirstNavigation.firstRemaining(7, 8));
        assertEquals(5, AppsFirstNavigation.firstRemaining(5, 21));
    }
    @Test public void backReturnsThroughEachLevelWithoutExitingHome() {
        AppsFirstNavigation.Page page = AppsFirstNavigation.Page.APPS;
        page = AppsFirstNavigation.back(page);
        assertEquals(AppsFirstNavigation.Page.RECOMMENDATIONS, page);
        page = AppsFirstNavigation.back(page);
        assertEquals(AppsFirstNavigation.Page.HOME, page);
        assertEquals(page, AppsFirstNavigation.back(page));
    }
}
