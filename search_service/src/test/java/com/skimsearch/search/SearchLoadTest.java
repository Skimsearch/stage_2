package com.skimsearch.search;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

public class SearchLoadTest {

    private static final int[] CONCURRENCY_LEVELS = {1, 5, 10, 20, 50};
    private static final int WARMUP_REQUESTS = 50;

    public static void main(String[] args) throws Exception {

        String baseUrl = args.length > 0
                ? args[0]
                : "http://localhost:7003";

        int totalRequests = args.length > 1
                ? Integer.parseInt(args[1])
                : 500;

        Path output = Path.of(
                args.length > 2
                        ? args[2]
                        : "benchmarks/results/search-load-initial.csv"
        );

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        URI searchUri = URI.create(baseUrl + "/search?q=alpha");

        warmUp(client, searchUri);

        Files.createDirectories(output.getParent());

        Files.writeString(
                output,
                "concurrency,requests,successes,errors,mean_ms,p95_ms,throughput_req_s\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        for (int concurrency : CONCURRENCY_LEVELS) {

            Result result = runTest(
                    client,
                    searchUri,
                    concurrency,
                    totalRequests
            );

            String line = String.format(
                    Locale.US,
                    "%d,%d,%d,%d,%.3f,%.3f,%.2f%n",
                    concurrency,
                    totalRequests,
                    result.successes(),
                    result.errors(),
                    result.meanMs(),
                    result.p95Ms(),
                    result.throughput()
            );

            Files.writeString(
                    output,
                    line,
                    StandardOpenOption.APPEND
            );

            System.out.printf(
                    Locale.US,
                    "Concurrency=%d | requests=%d | errors=%d | mean=%.3f ms | p95=%.3f ms | throughput=%.2f req/s%n",
                    concurrency,
                    totalRequests,
                    result.errors(),
                    result.meanMs(),
                    result.p95Ms(),
                    result.throughput()
            );
        }

        System.out.println();
        System.out.println("Results saved to " + output);
    }

    private static void warmUp(
            HttpClient client,
            URI uri
    ) throws IOException, InterruptedException {

        HttpRequest request = createRequest(uri);

        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            client.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );
        }
    }

    private static Result runTest(
            HttpClient client,
            URI uri,
            int concurrency,
            int totalRequests
    ) throws InterruptedException, ExecutionException {

        ExecutorService executor =
                Executors.newFixedThreadPool(concurrency);

        List<Future<RequestResult>> futures =
                new ArrayList<>();

        long testStart = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {

            futures.add(executor.submit(
                    () -> executeRequest(client, uri)
            ));
        }

        List<Double> latencies = new ArrayList<>();
        int successes = 0;
        int errors = 0;

        for (Future<RequestResult> future : futures) {

            RequestResult result = future.get();

            if (result.success()) {
                successes++;
                latencies.add(result.latencyMs());
            } else {
                errors++;
            }
        }

        long testEnd = System.nanoTime();

        executor.shutdown();

        double elapsedSeconds =
                (testEnd - testStart) / 1_000_000_000.0;

        double throughput =
                totalRequests / elapsedSeconds;

        Collections.sort(latencies);

        double mean = latencies.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);

        double p95 = percentile95(latencies);

        return new Result(
                successes,
                errors,
                mean,
                p95,
                throughput
        );
    }

    private static RequestResult executeRequest(
            HttpClient client,
            URI uri
    ) {

        HttpRequest request = createRequest(uri);

        long start = System.nanoTime();

        try {

            HttpResponse<Void> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            long end = System.nanoTime();

            double latencyMs =
                    (end - start) / 1_000_000.0;

            boolean success =
                    response.statusCode() >= 200
                            && response.statusCode() < 300;

            return new RequestResult(
                    success,
                    latencyMs
            );

        } catch (Exception e) {

            return new RequestResult(
                    false,
                    Double.NaN
            );
        }
    }

    private static HttpRequest createRequest(URI uri) {

        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
    }

    private static double percentile95(
            List<Double> values
    ) {

        if (values.isEmpty()) {
            return Double.NaN;
        }

        int index =
                (int) Math.ceil(values.size() * 0.95) - 1;

        return values.get(index);
    }

    private record RequestResult(
            boolean success,
            double latencyMs
    ) {}

    private record Result(
            int successes,
            int errors,
            double meanMs,
            double p95Ms,
            double throughput
    ) {}
}