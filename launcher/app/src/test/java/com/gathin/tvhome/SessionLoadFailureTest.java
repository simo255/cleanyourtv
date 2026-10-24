package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionLoadFailureTest {
 @Test public void corruptOrUndecryptableSessionsAreDefinitive() {
  for (Throwable t : new Throwable[]{new javax.crypto.AEADBadTagException(), new org.json.JSONException("bad"), new IllegalArgumentException(), new java.security.UnrecoverableKeyException(), new android.security.keystore.KeyPermanentlyInvalidatedException()}) {
   assertTrue(SessionLoadFailure.definitive(t)); assertTrue(SessionLoadFailure.definitive(new RuntimeException(t)));
  }
 }
 @Test public void keystoreAndIoHiccupsKeepTheSession() {
  for (Throwable t : new Throwable[]{new java.security.KeyStoreException(), new java.security.ProviderException(), new java.io.IOException(), new java.io.FileNotFoundException(), new java.security.InvalidKeyException()}) assertFalse(SessionLoadFailure.definitive(t));
 }
}
