package com.gathin.tvhome;
/** A stored Stremio session is deleted only when it can never decrypt again; Keystore hiccups keep it. */
final class SessionLoadFailure {
    private SessionLoadFailure() { }
    static boolean definitive(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof javax.crypto.AEADBadTagException || t instanceof org.json.JSONException
                    || t instanceof IllegalArgumentException || t instanceof java.security.UnrecoverableKeyException
                    || t instanceof android.security.keystore.KeyPermanentlyInvalidatedException) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }
}
