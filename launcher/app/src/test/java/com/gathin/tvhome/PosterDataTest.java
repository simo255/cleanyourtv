package com.gathin.tvhome;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class PosterDataTest {
    @Test public void readsCompleteBytesIncludingTheExactLimit() throws Exception {
        byte[] data = new byte[PosterData.MAX_BYTES];
        data[0] = 17;
        data[data.length - 1] = 42;
        assertArrayEquals(data, PosterData.read(new ByteArrayInputStream(data)));
    }

    @Test public void rejectsOversizedCacheOrProviderData() {
        assertThrows(IOException.class, () -> PosterData.read(
                new ByteArrayInputStream(new byte[PosterData.MAX_BYTES + 1])));
    }

    @Test public void rejectsMissingAndEmptyData() {
        assertThrows(IOException.class, () -> PosterData.read(null));
        assertThrows(IOException.class, () -> PosterData.read(new ByteArrayInputStream(new byte[0])));
    }

    @Test public void doesNotTreatInterruptedReadsAsCompleteImages() {
        assertThrows(IOException.class, () -> PosterData.read(new java.io.InputStream() {
            private int reads;
            @Override public int read() throws IOException {
                if (++reads > 20) throw new IOException("Interrupted");
                return 1;
            }
        }));
    }
}
