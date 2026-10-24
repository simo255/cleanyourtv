package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class TvProgramTest {
    private TvProgram item(long id, long channel, String link, long position, long duration, long engagement) throws Exception {
        return new TvProgram(new JSONObject().put("_id", id).put("channel_id", channel)
                .put("package_name", "com.example.tv").put("title", "Title " + id).put("intent_uri", link)
                .put("last_playback_position_millis", position).put("duration_millis", duration)
                .put("last_engagement_time_utc_millis", engagement), "preview", "Example", "Channel");
    }
    @Test public void allChannelsAreInterleavedBeforeAnyLargeChannelCanDominate() throws Exception {
        TvProgram a = item(1, 1, "example:a", 0, 0, 0), b = item(2, 1, "example:b", 0, 0, 0), c = item(3, 2, "example:c", 0, 0, 0);
        assertEquals(Arrays.asList(a, c, b), TvProgram.channels(Arrays.asList(a, b, c), ""));
        assertEquals(Collections.singletonList(c), TvProgram.channels(Arrays.asList(a, b, c), c.channelKey));
    }
    @Test public void sameAppLinkIsDeduplicatedWithoutMergingDifferentApps() throws Exception {
        TvProgram a = item(1, 1, "example:a", 0, 0, 0), b = item(2, 2, "example:a", 0, 0, 0);
        assertEquals(Collections.singletonList(a), TvProgram.channels(Arrays.asList(a, b), ""));
        TvProgram other = new TvProgram(new JSONObject().put("_id", 3).put("package_name", "com.other.app")
                .put("title", a.name).put("intent_uri", a.intent), "preview", "Other", "");
        assertEquals(2, TvProgram.channels(Arrays.asList(a, other), "").size());
    }
    @Test public void resumeCombinesWatchNextAndRealPreviewProgressButNotUntouchedLibraryItems() throws Exception {
        TvProgram watch = item(1, 1, "example:watch", 0, 100, 10), progress = item(2, 2, "example:progress", 20, 100, 0);
        TvProgram untouched = item(3, 3, "example:library", 0, 100, 0), ended = item(4, 3, "example:ended", 100, 100, 0);
        List<TvProgram> result = TvProgram.resume(Collections.singletonList(watch), Arrays.asList(watch, progress, untouched, ended));
        assertEquals(Arrays.asList(watch, progress), result);
    }
    @Test public void progressIsClampedAndUnknownDurationStaysUnknown() throws Exception {
        assertEquals(20, item(1, 1, "example:a", 20, 100, 0).percent());
        assertEquals(100, item(1, 1, "example:a", Long.MAX_VALUE, 10, 0).percent());
        assertEquals(-1, item(1, 1, "example:a", 0, 0, 0).percent());
    }
    @Test public void malformedOwnerMissingIdAndMissingLinkAreRejected() throws Exception {
        JSONObject base = new JSONObject().put("_id", 1).put("package_name", "com.example.tv").put("title", "Title").put("intent_uri", "example:a");
        JSONObject noId = new JSONObject(base.toString()); noId.remove("_id");
        JSONObject noLink = new JSONObject(base.toString()); noLink.remove("intent_uri");
        for (JSONObject row : new JSONObject[]{new JSONObject(base.toString()).put("package_name", "bad/package"), noId, noLink}) {
            assertFalse(new TvProgram(row, "preview", "Example", "").valid());
        }
        assertTrue(item(1, 1, "example:a", 0, 0, 0).valid());
    }
}
