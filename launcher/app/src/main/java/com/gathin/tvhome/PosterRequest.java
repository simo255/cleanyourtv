package com.gathin.tvhome;

/** A view keeps its decoded poster or its current request when catalog metadata is rebound. */
final class PosterRequest {
    final String address;
    final int generation;
    private volatile boolean failed;

    PosterRequest(String address, int generation) {
        this.address = address;
        this.generation = generation;
    }

    boolean reusable(String nextAddress, int nextGeneration, boolean drawn) {
        // A cancelled batch must retry a missing image, but an already drawn image stays visible.
        return address.equals(nextAddress) && (drawn || (!failed && generation == nextGeneration));
    }

    void failed() { failed = true; }
}
