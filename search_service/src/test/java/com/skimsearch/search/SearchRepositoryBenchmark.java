package com.skimsearch.search;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
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
    private List<String> queries;
    private int queryIndex;

    @Param({"../experiments/queries.txt"})
    public String queryFile;

    @Param({"alpha"})
    public String filteredTerm;

    @Param({"Test Author"})
    public String author;

    @Param({"en"})
    public String language;

    @Param({"1998"})
    public int year;

    @Setup(Level.Trial)
    public void setup() throws IOException {

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

        queries = Files.readAllLines(Path.of(queryFile))
                .stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .toList();

        if (queries.isEmpty()) {
            throw new IllegalStateException(
                    "Query workload is empty: " + queryFile
            );
        }

        queryIndex = 0;
    }

    @Benchmark
    public List<SearchResult> keywordSearch() throws SQLException {

        String term = queries.get(queryIndex);

        queryIndex = (queryIndex + 1) % queries.size();

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
                filteredTerm,
                author,
                language,
                year,
                0,
                20
        );
    }
}