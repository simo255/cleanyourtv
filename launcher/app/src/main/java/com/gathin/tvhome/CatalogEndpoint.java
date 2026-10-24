package com.gathin.tvhome;

import java.net.URI;

/** Redirects stay within the selected public catalog or artwork service. */
final class CatalogEndpoint {
    static boolean publicCinemeta(URI uri) {
        return publicHttps(uri)
                && ("v3-cinemeta.strem.io".equals(uri.getHost()) || "cinemeta-catalogs.strem.io".equals(uri.getHost()));
    }

    static boolean publicPoster(URI uri) {
        return publicHttps(uri)
                && ("images.metahub.space".equals(uri.getHost()) || "live.metahub.space".equals(uri.getHost()));
    }

    private static boolean publicHttps(URI uri) {
        return "https".equals(uri.getScheme()) && uri.getPort() == -1 && uri.getUserInfo() == null
                && uri.getRawQuery() == null && uri.getRawFragment() == null
                && uri.getHost() != null;
    }
}
