package com.skimsearch.ingestion.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {

        Options options = new OptionsBuilder()
                .include(IngestionBenchmark.class.getSimpleName())
                .forks(1)
                .jvmArgs(
                        "-Xms512m",
                        "-Xmx512m"
                )
                .result("results/ingestion-jmh.json")
                .resultFormat(ResultFormatType.JSON)
                .build();

        new Runner(options).run();
    }
}