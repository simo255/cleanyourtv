package com.gathin.tvhome;
/** Largest power-of-two subsample that still covers the card, so posters are never decoded below their slot. */
final class PosterSampling {
    private PosterSampling() { }
    static int sampleSize(int width, int height, int targetWidth, int targetHeight) {
        int sample = 1;
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return sample;
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2;
        return sample;
    }
    static int cacheBytes(long maxHeap) { return (int) Math.max(6L << 20, Math.min(24L << 20, maxHeap / 8)); }
    static int evictions(long[] sizesOldestFirst, long total, long lowWatermark) {
        int count = 0;
        while (count < sizesOldestFirst.length && total > lowWatermark) total -= sizesOldestFirst[count++];
        return count;
    }
}
