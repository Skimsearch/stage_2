package com.skimsearch.search;

import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(
        value = 1,
        jvmArgsAppend = {"-Xms512m", "-Xmx512m"}
)
@Threads(1)
@State(Scope.Thread)
public class SearchTokenizerBenchmark {

    @Param({"ALPHA"})
    public String query;

    @Benchmark
    public List<String> tokenizeQuery() {
        return SearchTokenizer.tokenize(query);
    }
}