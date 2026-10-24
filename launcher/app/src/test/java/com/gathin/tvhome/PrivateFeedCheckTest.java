package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrivateFeedCheckTest {
 @Test public void emptyHistoryAndEmptyCatalogKeepThePreviousCache() { assertTrue(PrivateFeedCheck.keepPreviousCache(0,0,true)); }
 @Test public void emptyHistoryWithRecommendationsIsAccepted() { assertFalse(PrivateFeedCheck.keepPreviousCache(0,1,true));assertFalse(PrivateFeedCheck.keepPreviousCache(1,0,true)); }
 @Test public void firstSyncWithNothingKnownIsAccepted() { assertFalse(PrivateFeedCheck.keepPreviousCache(0,0,false)); }
}
