package edu.itmo.spring_boot_project.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public final class FileStorage {
    private final Path directory;
    private final Properties users;

    public FileStorage(@Value("${app.storage-dir:data}") String directory,
                       @Value("${app.initial-password:}") String password, PasswordEncoder encoder) throws IOException {
        this.directory = Path.of(directory);
        Files.createDirectories(this.directory);
        users = read("users.properties");
        if (users.isEmpty()) {
            if (password.isBlank() || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException("Set APP_INITIAL_PASSWORD (1 to 72 UTF-8 bytes) for first start");
            users.setProperty("username", "admin");
            users.setProperty("passwordHash", encoder.encode(password));
            write("users.properties", users);
        }
    }

    public String username() {
        return users.getProperty("username");
    }

    public String passwordHash() {
        return users.getProperty("passwordHash");
    }

    public record Entry(String id, String text) {
    }

    public synchronized List<Entry> entries() throws IOException {
        Properties data = read("entries.properties");
        return data.stringPropertyNames().stream().sorted().map(id -> new Entry(id, data.getProperty(id))).toList();
    }

    public synchronized Entry add(String text) throws IOException {
        Properties data = read("entries.properties");
        Entry entry = new Entry(UUID.randomUUID().toString(), text);
        data.setProperty(entry.id(), entry.text());
        write("entries.properties", data);
        return entry;
    }

    private Properties read(String name) throws IOException {
        Properties result = new Properties();
        Path path = directory.resolve(name);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                result.load(reader);
            }
        }
        return result;
    }

    private void write(String name, Properties data) throws IOException {
        Path temporary = Files.createTempFile(directory, "storage-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                data.store(writer, "REST API storage");
            }
            try {
                Files.move(temporary, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
