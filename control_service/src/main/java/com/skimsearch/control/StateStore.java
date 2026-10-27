package com.skimsearch.control;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private final Map<Integer, State> stateMap = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public StateStore(String stateFilePath) {
        this.filePath = Paths.get(stateFilePath);
        load();
    }

    public synchronized void updateState(int bookId, State newState) {
        stateMap.put(bookId, newState);
        save();
    }

    public State getState(int bookId) {
        return stateMap.getOrDefault(bookId, State.PENDING);
    }

    public Map<Integer, State> getAllStates() {
        return Collections.unmodifiableMap(stateMap);
    }

    private synchronized void save() {
        try {
            if (filePath.getParent() != null && !Files.exists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(stateMap, writer);
            }
        } catch (IOException e) {
            System.err.println("Error saving state to " + filePath + ": " + e.getMessage());
        }
    }

    private synchronized void load() {
        if (!Files.exists(filePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type type = new TypeToken<Map<Integer, State>>() {}.getType();
            Map<Integer, State> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                stateMap.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("Error reading state from " + filePath + ": " + e.getMessage());
        }
    }
}