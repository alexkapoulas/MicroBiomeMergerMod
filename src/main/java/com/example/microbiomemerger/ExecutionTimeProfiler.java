package com.example.microbiomemerger;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

public class ExecutionTimeProfiler {
    // Create a Recorder that can record values from 1 ns to 10 seconds with 3 significant figures
    private final Recorder recorder = new Recorder(1, 10_000_000_000L, 3);

    // Record an execution time (in nanoseconds)
    public void recordExecutionTime(String method, long elapsedNanos) {
        recorder.recordValue(elapsedNanos);
    }

    // Get the average execution time in nanoseconds
    public double getAverage() {
        Histogram histogram = recorder.getIntervalHistogram();
        return histogram.getMean();
    }

    // Get the value at a given percentile (e.g. 90th for worst 10%, 99th for worst 1%)
    public long getValueAtPercentile(double percentile) {
        Histogram histogram = recorder.getIntervalHistogram();
        return histogram.getValueAtPercentile(percentile);
    }
}

