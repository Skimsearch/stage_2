package com.skimsearch.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Runs the whole Stage 2 microbenchmark suite with the methodology of the
 * assignment: 5 warm-up iterations, 10 measurement iterations, fixed heap.
 *
 * Raw results go to benchmarks/results/ (committed to the repository, unlike
 * target/, which "mvn clean" deletes), together with run-info.txt: date, git
 * commit, machine and JVM settings, so every number can be traced to the exact
 * code and host that produced it.
 *
 * Index updates are repeated with 1, 2 and 4 threads (one factor at a time).
 */
public final class BenchmarkRunner {

    private static final Path RESULTS_DIR = Path.of("benchmarks", "results");
    private static final String[] JVM_ARGS = {"-Xms2G", "-Xmx2G"};

    private BenchmarkRunner() {}

    public static void main(String[] args) throws RunnerException, IOException {
        Files.createDirectories(RESULTS_DIR);
        writeRunInfo();

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
                .result(RESULTS_DIR.resolve("jmh-" + benchmark.getSimpleName() + "-t" + threads + ".csv").toString())
                .build();
        new Runner(options).run();
    }

    private static void writeRunInfo() throws IOException {
        Runtime runtime = Runtime.getRuntime();
        List<String> lines = List.of(
                "date: " + Instant.now(),
                "git commit: " + gitCommit(),
                "os: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")",
                "logical cpus: " + runtime.availableProcessors(),
                "java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                "benchmark jvm args: " + String.join(" ", JVM_ARGS),
                "methodology: 5 warm-up x 1 s, 10 measurement x 1 s, 1 fork",
                "command: " + System.getProperty("sun.java.command"),
                "note: add CPU model, RAM and disk type by hand in experiments/README.md; the JVM cannot read them portably"
        );
        Files.write(RESULTS_DIR.resolve("run-info.txt"), lines, StandardCharsets.UTF_8);
        lines.forEach(System.out::println);
    }

    /** Short commit hash, marked "-dirty" when tracked files have uncommitted changes. */
    private static String gitCommit() {
        String hash = git("rev-parse", "--short", "HEAD");
        if (hash == null) {
            return "unknown (not a git checkout or git not installed)";
        }
        String changes = git("status", "--porcelain", "--untracked-files=no");
        return changes == null || changes.isBlank() ? hash : hash + "-dirty";
    }

    private static String git(String... arguments) {
        try {
            String[] command = new String[arguments.length + 1];
            command[0] = "git";
            System.arraycopy(arguments, 0, command, 1, arguments.length);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
