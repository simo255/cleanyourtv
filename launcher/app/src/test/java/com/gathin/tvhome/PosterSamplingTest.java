package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterSamplingTest {
 @Test public void extremeAspectRatiosStayWithinPixelBudget() {
     for (int[] size : new int[][]{{1, Integer.MAX_VALUE}, {Integer.MAX_VALUE, 1},
             {Integer.MAX_VALUE, Integer.MAX_VALUE}, {500, 100000}, {100000, 500}}) {
         int sample = PosterSampling.sampleSize(size[0], size[1], 300, 450);
         long pixels = ((size[0] + sample - 1L) / sample) * ((size[1] + sample - 1L) / sample);
         assertTrue(pixels <= PosterSampling.MAX_PIXELS);
         assertEquals(0, sample & (sample - 1));
     }
 }
 @Test public void unmeasuredCardStillHasAPixelBudget() {
     int sample = PosterSampling.sampleSize(100000, 100000, 0, 0);
     assertTrue(((100000 + sample - 1L) / sample) * ((100000 + sample - 1L) / sample) <= PosterSampling.MAX_PIXELS);
 }
 @Test public void neverDecodesBelowTheCard() {assertEquals(1,PosterSampling.sampleSize(342,513,272,408));assertEquals(2,PosterSampling.sampleSize(1000,1500,272,408));assertEquals(4,PosterSampling.sampleSize(2000,3000,272,408));assertEquals(1,PosterSampling.sampleSize(0,0,0,0));}
 @Test public void cacheScalesWithHeapWithinBounds() {assertEquals(6<<20,PosterSampling.cacheBytes(32L<<20));assertEquals(16<<20,PosterSampling.cacheBytes(128L<<20));assertEquals(24<<20,PosterSampling.cacheBytes(512L<<20));}
 @Test public void evictsOldestUntilUnderTheLowWatermark() {assertEquals(2,PosterSampling.evictions(new long[]{2000000,2000000,9000000},13000000,9000000));}
 @Test public void nothingToEvictUnderTheCap() {assertEquals(0,PosterSampling.evictions(new long[]{100},100,9000000));}
}
