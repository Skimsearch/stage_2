package com.skimsearch.benchmark;

import com.skimsearch.indexing.BookMetadata;
import com.skimsearch.indexing.MetadataExtractor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/** Metadata extraction from a Project Gutenberg header. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class MetadataExtractorBenchmark {

    private final String header = """
            The Project Gutenberg eBook of Pride and Prejudice

            This eBook is for the use of anyone anywhere in the United States and
            most other parts of the world at no cost and with almost no restrictions
            whatsoever.

            Title: Pride and Prejudice

            Author: Jane Austen

            Release date: June 1, 1998 [eBook #1342]
                            Most recently updated: October 29, 2024

            Language: English

            Credits: Chuck Greif and the Online Distributed Proofreading Team
            """;

    @Benchmark
    public BookMetadata extract() {
        return MetadataExtractor.extract(1342, header);
    }
}
