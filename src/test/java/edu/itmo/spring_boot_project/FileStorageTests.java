package edu.itmo.spring_boot_project;

import edu.itmo.spring_boot_project.service.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageTests {
    @TempDir
    Path directory;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void newStorageHasNoEntries() throws Exception {
        var storage = new FileStorage(directory.toString(), "test-password", encoder);

        var entries = storage.entries();

        assertTrue(entries.isEmpty());
    }

    @Test
    void savedEntryRemainsAfterReopeningStorage() throws Exception {
        var storage = new FileStorage(directory.toString(), "test-password", encoder);
        var saved = storage.add("Привет <script> & = новая строка\nтекст");
        var reopened = new FileStorage(directory.toString(), "", encoder);

        var entries = reopened.entries();

        assertEquals(List.of(saved), entries);
    }

    @Test
    void initialPasswordIsStoredAsBcryptHash() throws Exception {
        var storage = new FileStorage(directory.toString(), "test-password", encoder);

        String hash = storage.passwordHash();

        assertTrue(encoder.matches("test-password", hash));
        assertFalse(Files.readString(directory.resolve("users.properties")).contains("test-password"));
    }

    @Test
    void reopeningDoesNotReplaceExistingPassword() throws Exception {
        new FileStorage(directory.toString(), "first-password", encoder);

        var reopened = new FileStorage(directory.toString(), "different-password", encoder);

        assertTrue(encoder.matches("first-password", reopened.passwordHash()));
        assertFalse(encoder.matches("different-password", reopened.passwordHash()));
    }
}
