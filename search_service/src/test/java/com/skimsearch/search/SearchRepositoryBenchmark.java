package com.skimsearch.search;

import org.openjdk.jmh.annotations.*;

import java.nio.file.Path;
import java.sql.SQLException;
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
@State(Scope.Benchmark)
public class SearchRepositoryBenchmark {

    private SearchRepository repository;

    @Setup(Level.Trial)
    public void setup() {
        String dataDir = System.getProperty(
                "dataDir",
                "../data/check"
        );

        SearchConfig config = new SearchConfig(
                7003,
                Path.of(dataDir),
                250
        );

        SearchDatabase database = new SearchDatabase(config);
        repository = new SearchRepository(database);
    }

    @Benchmark
    public List<SearchResult> keywordSearch() throws SQLException {
        return repository.search(
                "alpha",
                null,
                null,
                null,
                0,
                20
        );
    }

    @Benchmark
    public List<SearchResult> filteredSearch() throws SQLException {
        return repository.search(
                "alpha",
                "Test Author",
                "en",
                1998,
                0,
                20
        );
    }
}