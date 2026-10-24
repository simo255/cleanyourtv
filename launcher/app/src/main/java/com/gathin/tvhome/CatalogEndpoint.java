package com.gathin.tvhome;

import java.net.URI;

/** Redirects stay within the selected public catalog or artwork service. */
final class CatalogEndpoint {
    static boolean tmdbApi(URI uri) {
        return "https".equals(uri.getScheme()) && "api.themoviedb.org".equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getRawFragment() == null
                && uri.getPath().matches("/3/(discover/(movie|tv)|(movie|tv)/[1-9][0-9]*)");
    }
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
