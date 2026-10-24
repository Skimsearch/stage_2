package com.skimsearch.benchmark;

import com.skimsearch.indexing.Tokenizer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Text parsing: regex tokenization and term-frequency counting (pure CPU, no I/O). */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class TokenizerBenchmark {

    /** 10k words is a short story, 100k a novel (Pride and Prejudice has ~120k), 500k a very long book. */
    @Param({"10000", "100000", "500000"})
    public int words;

    private String text;

    @Setup
    public void setUp() {
        text = SyntheticData.text(words, SyntheticData.vocabulary(8_000), 42L);
    }

    @Benchmark
    public void tokenize(Blackhole blackhole) {
        blackhole.consume(Tokenizer.tokenize(text));
    }

    @Benchmark
    public Map<String, Integer> termFrequencies() {
        return Tokenizer.termFrequencies(text);
    }
}
