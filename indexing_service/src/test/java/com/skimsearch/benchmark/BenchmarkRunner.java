package com.skimsearch.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Runs the whole Stage 2 microbenchmark suite with the methodology of the
 * assignment: 5 warm-up iterations, 10 measurement iterations, fixed heap,
 * results exported to CSV in target/.
 *
 * Index updates are repeated with 1, 2 and 4 threads (one factor at a time).
 */
public final class BenchmarkRunner {

    private static final String[] JVM_ARGS = {"-Xms2G", "-Xmx2G"};

    private BenchmarkRunner() {}

    public static void main(String[] args) throws RunnerException {
        printHost();

        run(TokenizerBenchmark.class, 1);
        run(MetadataExtractorBenchmark.class, 1);
        for (int threads : new int[] {1, 2, 4}) {
            run(IndexUpdateBenchmark.class, threads);
        }
    }

    private static void run(Class<?> benchmark, int threads) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(benchmark.getSimpleName())
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(10)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .threads(threads)
                .jvmArgs(JVM_ARGS)
                .resultFormat(ResultFormatType.CSV)
                .result("target/jmh-" + benchmark.getSimpleName() + "-t" + threads + ".csv")
                .build();
        new Runner(options).run();
    }

    private static void printHost() {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("HOST: " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + ", " + runtime.availableProcessors() + " logical CPUs"
                + ", max heap of this JVM " + runtime.maxMemory() / (1024 * 1024) + " MB"
                + ", Java " + System.getProperty("java.version"));
    }
}
