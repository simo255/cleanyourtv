package com.gathin.tvhome;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class StremioViewingEvidenceTest {
 @Test public void accountBindingAndKeyOrderDoNotCountAsChange() throws Exception {
  JSONObject a=new JSONObject("{\"movies\":[{\"id\":\"tt1\",\"type\":\"movie\"}],\"series\":[],\"account\":\"a\"}");
  JSONObject b=new JSONObject("{\"account\":\"b\",\"series\":[],\"movies\":[{\"type\":\"movie\",\"id\":\"tt1\"}]}");assertTrue(StremioViewing.sameEvidence(a,b));
 }
 @Test public void aNewlyWatchedMovieOrSeasonCountIsAChange() throws Exception {
  JSONObject a=new JSONObject("{\"movies\":[],\"series\":[{\"seasons\":[{\"watched\":1}]}]}");
  JSONObject b=new JSONObject(a.toString());b.getJSONArray("movies").put(new JSONObject().put("id","tt1"));assertFalse(StremioViewing.sameEvidence(a,b));
  b=new JSONObject(a.toString());b.getJSONArray("series").getJSONObject(0).getJSONArray("seasons").getJSONObject(0).put("watched",2);assertFalse(StremioViewing.sameEvidence(a,b));
 }
 @Test public void emptySnapshotsAreEqual() {assertTrue(StremioViewing.sameEvidence(new JSONObject(),new JSONObject()));}
}
