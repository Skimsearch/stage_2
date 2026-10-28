package com.skimsearch.ingestion.benchmark;

import com.skimsearch.ingestion.DatalakeWriter;
import com.skimsearch.ingestion.GutenbergClient;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class IngestionBenchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {

        GutenbergClient gutenbergClient;
        DatalakeWriter datalakeWriter;

        String sampleText;
        String header;
        String body;

        int counter;

        @Setup
        public void setup() throws Exception {

            gutenbergClient =
                    new GutenbergClient();

            Path tempDir =
                    Files.createTempDirectory(
                            "ingestion-benchmark"
                    );

            datalakeWriter =
                    new DatalakeWriter(
                            tempDir
                    );

            header =
                    """
                    Title: Pride and Prejudice
                    Author: Jane Austen
                    Language: English
                    Release date: June 1, 1998
                    """;

            body =
                    "This is an example body text. ".repeat(1000);

            sampleText =
                    header
                    + "\n"
                    + "*** START OF THE PROJECT GUTENBERG EBOOK PRIDE AND PREJUDICE ***"
                    + "\n"
                    + body
                    + "\n"
                    + "*** END OF THE PROJECT GUTENBERG EBOOK PRIDE AND PREJUDICE ***";

            counter = 100000;
        }
    }

    @Benchmark
    public GutenbergClient.BookContent splitBook(
            BenchmarkState state
    ) {

        return state.gutenbergClient
                .splitBook(
                        state.sampleText
                );
    }

    @Benchmark
    public Path batchPath(
            BenchmarkState state
    ) {

        return state.datalakeWriter
                .getBatchDirectory(
                        1342
                );
    }

    @Benchmark
    public void writeBook(
            BenchmarkState state
    ) throws Exception {

        int bookId =
                state.counter++;

        state.datalakeWriter.writeBook(
                bookId,
                state.header,
                state.body
        );
    }
}
