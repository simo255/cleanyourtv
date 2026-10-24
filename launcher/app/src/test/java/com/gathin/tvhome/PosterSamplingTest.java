package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterSamplingTest {
 @Test public void neverDecodesBelowTheCard() {assertEquals(1,PosterSampling.sampleSize(342,513,272,408));assertEquals(2,PosterSampling.sampleSize(1000,1500,272,408));assertEquals(4,PosterSampling.sampleSize(2000,3000,272,408));assertEquals(1,PosterSampling.sampleSize(0,0,0,0));}
 @Test public void cacheScalesWithHeapWithinBounds() {assertEquals(6<<20,PosterSampling.cacheBytes(32L<<20));assertEquals(16<<20,PosterSampling.cacheBytes(128L<<20));assertEquals(24<<20,PosterSampling.cacheBytes(512L<<20));}
 @Test public void evictsOldestUntilUnderTheLowWatermark() {assertEquals(2,PosterSampling.evictions(new long[]{2000000,2000000,9000000},13000000,9000000));}
 @Test public void nothingToEvictUnderTheCap() {assertEquals(0,PosterSampling.evictions(new long[]{100},100,9000000));}
}
