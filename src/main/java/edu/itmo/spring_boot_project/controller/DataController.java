package edu.itmo.spring_boot_project.controller;

import edu.itmo.spring_boot_project.service.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.List;

@RestController
public class DataController {
    private final FileStorage storage;

    public DataController(FileStorage storage) {
        this.storage = storage;
    }

    public record CreateRequest(String text) {
    }

    @GetMapping("/api/data")
    public List<FileStorage.Entry> get() throws IOException {
        return storage.entries().stream().map(e -> new FileStorage.Entry(e.id(), HtmlUtils.htmlEscape(e.text()))).toList();
    }

    @PostMapping("/api/data")
    @ResponseStatus(HttpStatus.CREATED)
    public FileStorage.Entry add(@RequestBody CreateRequest request) throws IOException {
        if (request.text() == null || request.text().isBlank() || request.text().length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        FileStorage.Entry entry = storage.add(request.text());
        return new FileStorage.Entry(entry.id(), HtmlUtils.htmlEscape(entry.text()));
    }
}