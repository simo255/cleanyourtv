package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** The same byte limit applies to provider images and disk cache reads. */
final class PosterData {
    static final int MAX_BYTES = 3_000_000;

    private PosterData() { }

    static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing poster");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_BYTES - output.size()) throw new IOException("Poster too large");
            output.write(buffer, 0, count);
        }
        if (output.size() == 0) throw new IOException("Empty poster");
        return output.toByteArray();
    }
}
