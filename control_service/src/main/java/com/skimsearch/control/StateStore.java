
package com.skimsearch.control;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public class StateStore {

    public enum State {
        PENDING,
        INGESTING,
        DOWNLOADED,
        INDEXING,
        INDEXED,
        FAILED
    }

    private final Path filePath;
    private final Map<Integer, State> stateMap =
            new ConcurrentHashMap<>();

    private final Gson gson =
            new GsonBuilder().setPrettyPrinting().create();

    public StateStore(String stateFilePath) {
        if (stateFilePath == null || stateFilePath.isBlank()) {
            throw new IllegalArgumentException(
                    "State file path cannot be null or empty");
        }

        this.filePath = Paths.get(stateFilePath);
        load();
    }

    public synchronized void updateState(int bookId, State newState) {
        if (newState == null) {
            throw new IllegalArgumentException(
                    "State cannot be null");
        }

        State previousState = stateMap.put(bookId, newState);

        try {
            save();
        } catch (RuntimeException e) {
            // Revertir el cambio en memoria si no se pudo persistir.
            if (previousState == null) {
                stateMap.remove(bookId);
            } else {
                stateMap.put(bookId, previousState);
            }

            throw e;
        }
    }

    public State getState(int bookId) {
        return stateMap.getOrDefault(bookId, State.PENDING);
    }

    public Map<Integer, State> getAllStates() {
        return Collections.unmodifiableMap(
                new HashMap<>(stateMap));
    }

    private void save() {
        Path parent = filePath.toAbsolutePath().getParent();
        Path tempFile = null;

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            tempFile = Files.createTempFile(
                    parent, "state-", ".tmp");

            try (var writer = Files.newBufferedWriter(tempFile)) {
                gson.toJson(stateMap, writer);
            }

            try {
                Files.move(
                        tempFile,
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile,
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save state to " + filePath, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    System.err.println(
                            "Warning: could not delete temporary state file: "
                                    + e.getMessage());
                }
            }
        }
    }

    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type type = new TypeToken<Map<Integer, State>>() {
            }.getType();

            Map<Integer, State> loaded = gson.fromJson(reader, type);

            if (loaded != null) {
                for (Map.Entry<Integer, State> entry
                        : loaded.entrySet()) {

                    if (entry.getKey() == null
                            || entry.getValue() == null) {
                        throw new IllegalStateException(
                                "State file contains a null book ID or state");
                    }
                }

                stateMap.putAll(loaded);
            }

        } catch (IOException | JsonParseException
                | IllegalStateException e) {
            throw new IllegalStateException(
                    "Could not load valid state data from " + filePath, e);
        }
    }
}
