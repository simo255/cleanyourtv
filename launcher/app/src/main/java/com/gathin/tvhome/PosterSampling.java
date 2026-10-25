package com.gathin.tvhome;
/** Preserve card detail for normal posters, with a hard pixel budget for extreme aspect ratios. */
final class PosterSampling {
    static final long MAX_PIXELS = 2_000_000;
    private PosterSampling() { }
    static int sampleSize(int width, int height, int targetWidth, int targetHeight) {
        int sample = 1;
        if (width <= 0 || height <= 0) return sample;
        while (targetWidth > 0 && targetHeight > 0 && sample < (1 << 30) && width / (sample * 2L) >= targetWidth
                && height / (sample * 2L) >= targetHeight) sample *= 2;
        // Round up: decoders can retain a partial pixel at the image edges.
        while (sample < (1 << 30) && ((width + sample - 1L) / sample)
                * ((height + sample - 1L) / sample) > MAX_PIXELS) sample *= 2;
        return sample;
    }
    static int cacheBytes(long maxHeap) { return (int) Math.max(6L << 20, Math.min(24L << 20, maxHeap / 8)); }
    static int evictions(long[] sizesOldestFirst, long total, long lowWatermark) {
        int count = 0;
        while (count < sizesOldestFirst.length && total > lowWatermark) total -= sizesOldestFirst[count++];
        return count;
    }
}
