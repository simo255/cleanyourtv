package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosterRequestTest {
    @Test public void metadataRefreshKeepsVisibleArtworkEvenWhenTheOldBatchWasCancelled() {
        PosterRequest request = new PosterRequest("https://example.test/a.jpg", 1);
        assertTrue(request.reusable(request.address, 2, true));
    }
    @Test public void cancelledRequestWithNoImageMustRestartInsteadOfStayingBlank() {
        PosterRequest request = new PosterRequest("https://example.test/a.jpg", 1);
        assertTrue(request.reusable(request.address, 1, false));
        assertFalse(request.reusable(request.address, 2, false));
    }
    @Test public void recycledViewNeverRetainsAnotherTitlesArtwork() {
        PosterRequest request = new PosterRequest("https://example.test/a.jpg", 1);
        assertFalse(request.reusable("https://example.test/b.jpg", 1, true));
        assertFalse(request.reusable("", 1, true));
    }
    @Test public void failedNetworkRequestCanRetryWithinTheSameCatalogBatch() {
        PosterRequest request = new PosterRequest("https://example.test/a.jpg", 1);
        request.failed();
        assertFalse(request.reusable(request.address, 1, false));
        assertTrue(request.reusable(request.address, 1, true));
    }
}
