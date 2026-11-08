package edu.itmo.spring_boot_project.controller;

import edu.itmo.spring_boot_project.service.FileStorage;
import edu.itmo.spring_boot_project.util.JwtTokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {
    private final FileStorage storage;
    private final PasswordEncoder encoder;
    private final JwtTokenUtil jwt;

    public AuthController(FileStorage storage, PasswordEncoder encoder, JwtTokenUtil jwt) {
        this.storage = storage;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record LoginRequest(String username, String password) {
    }

    public record TokenResponse(String token, String type) {
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null || request.username().length() > 100
                || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        boolean matches = encoder.matches(request.password(), storage.passwordHash());
        if (!storage.username().equals(request.username()) || !matches)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return new TokenResponse(jwt.generateToken(request.username()), "Bearer");
    }
}