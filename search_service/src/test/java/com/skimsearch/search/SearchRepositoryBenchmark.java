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
        @Param({"alpha"})
        public String term;

        @Param({"Test Author"})
        public String author;

        @Param({"en"})
        public String language;

        @Param({"1998"})
        public int year;

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
                term,
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
                term,
                author,
                language,
                year,
                0,
                20
        );
        }
}