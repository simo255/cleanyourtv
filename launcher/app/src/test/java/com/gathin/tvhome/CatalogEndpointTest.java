package com.gathin.tvhome;

import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;

public class CatalogEndpointTest {
    @Test public void officialPublicCatalogAndItsRedirectAreAllowed() {
        assertTrue(CatalogEndpoint.publicCinemeta(URI.create("https://v3-cinemeta.strem.io/catalog/movie/top.json")));
        assertTrue(CatalogEndpoint.publicCinemeta(URI.create("https://cinemeta-catalogs.strem.io/top/catalog/series/top.json")));
    }

    @Test public void privateAndUntrustedRedirectTargetsAreRejected() {
        for (String address : new String[]{"https://www.strem.io/trakt/addon/private/", "https://example.test/catalog.json",
                "https://cinemeta-catalogs.strem.io.evil.test/", "http://cinemeta-catalogs.strem.io/",
                "https://user@cinemeta-catalogs.strem.io/", "https://cinemeta-catalogs.strem.io:443/",
                "https://cinemeta-catalogs.strem.io/?token=private", "https://cinemeta-catalogs.strem.io/#private"}) {
            assertFalse(address, CatalogEndpoint.publicCinemeta(URI.create(address)));
        }
    }

    @Test public void officialPosterAndItsLiveServiceRedirectAreAllowed() {
        assertTrue(CatalogEndpoint.publicPoster(URI.create("https://images.metahub.space/poster/small/tt123/img")));
        assertTrue(CatalogEndpoint.publicPoster(URI.create("https://live.metahub.space/poster/small/tt123/img")));
        assertFalse(CatalogEndpoint.publicCinemeta(URI.create("https://live.metahub.space/poster/small/tt123/img")));
    }

    @Test public void artworkRedirectsCannotReachPrivateCatalogsOrUntrustedHosts() {
        for (String address : new String[]{"https://www.strem.io/trakt/addon/private/", "https://v3-cinemeta.strem.io/",
                "https://live.metahub.space.evil.test/", "http://live.metahub.space/", "https://user@live.metahub.space/",
                "https://live.metahub.space:443/", "https://live.metahub.space/?token=private", "https://live.metahub.space/#private"}) {
            assertFalse(address, CatalogEndpoint.publicPoster(URI.create(address)));
        }
    }
}
