package edu.itmo.spring_boot_project;

import edu.itmo.spring_boot_project.service.FileStorage;
import edu.itmo.spring_boot_project.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringBootProjectApplicationTests {
    private static final String SECRET = "test-secret-at-least-32-bytes-long!";
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @TempDir
    static Path directory;
    @LocalServerPort
    int port;
    @Autowired
    JwtTokenUtil jwt;
    @Autowired
    FileStorage storage;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage-dir", () -> directory.toString());
        registry.add("app.initial-password", () -> "test-password");
        registry.add("jwt.secret", () -> SECRET);
    }

    @BeforeEach
    void clearEntries() throws Exception {
        Files.deleteIfExists(directory.resolve("entries.properties"));
    }

    @Test
    void loginReturnsTokenForCorrectPassword() throws Exception {
        var response = post("/auth/login", """
                {"username":"admin","password":"test-password"}
                """, null);

        assertEquals(200, response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("Bearer", body.get("type").asText());
        assertEquals("admin", jwt.getUsernameFromToken(body.get("token").asText()));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        var response = post("/auth/login", """
                {"username":"admin","password":"wrong"}
                """, null);

        assertEquals(401, response.statusCode());
    }

    @Test
    void loginRejectsUnknownUser() throws Exception {
        var response = post("/auth/login", """
                {"username":"unknown","password":"test-password"}
                """, null);

        assertEquals(401, response.statusCode());
    }

    @Test
    void loginRejectsMissingPassword() throws Exception {
        var response = post("/auth/login", "{\"username\":\"admin\"}", null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void getDataRejectsMissingToken() throws Exception {
        var response = getData(null);

        assertEquals(401, response.statusCode());
    }

    @Test
    void createDataRejectsMissingToken() throws Exception {
        var response = post("/api/data", "{\"text\":\"blocked\"}", null);

        assertEquals(401, response.statusCode());
    }

    @Test
    void getDataRejectsMalformedToken() throws Exception {
        var response = getData("invalid");

        assertEquals(401, response.statusCode());
    }

    @Test
    void getDataRejectsTokenForUnknownUser() throws Exception {
        String token = jwt.generateToken("unknown");

        var response = getData(token);

        assertEquals(401, response.statusCode());
    }

    @Test
    void getDataRejectsExpiredToken() throws Exception {
        String token = new JwtTokenUtil(SECRET, -1000).generateToken("admin");

        var response = getData(token);

        assertEquals(401, response.statusCode());
    }

    @Test
    void getDataRejectsTokenSignedWithAnotherKey() throws Exception {
        String token = new JwtTokenUtil("another-test-secret-at-least-32-bytes", 60000).generateToken("admin");

        var response = getData(token);

        assertEquals(401, response.statusCode());
    }

    @Test
    void getDataReturnsEmptyList() throws Exception {
        var response = getData(adminToken());

        assertEquals(200, response.statusCode());
        assertEquals(JSON.readTree("[]"), JSON.readTree(response.body()));
    }

    @Test
    void getDataReturnsSavedEntryWithEscapedText() throws Exception {
        FileStorage.Entry saved = storage.add("<script>alert(1)</script>");

        var response = getData(adminToken());

        assertEquals(200, response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(1, body.size());
        assertEquals(saved.id(), body.get(0).get("id").asText());
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", body.get(0).get("text").asText());
    }

    @Test
    void createDataReturnsCreatedEntry() throws Exception {
        var response = post("/api/data", "{\"text\":\"My first note\"}", adminToken());

        assertEquals(201, response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        assertFalse(body.get("id").asText().isBlank());
        assertEquals("My first note", body.get("text").asText());
    }

    @Test
    void createDataEscapesHtmlInResponse() throws Exception {
        var response = post("/api/data", "{\"text\":\"<script>alert(1)</script>\"}", adminToken());

        assertEquals(201, response.statusCode());
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", JSON.readTree(response.body()).get("text").asText());
    }

    @Test
    void createDataRejectsBlankText() throws Exception {
        var response = post("/api/data", "{\"text\":\" \"}", adminToken());

        assertEquals(400, response.statusCode());
    }

    @Test
    void createDataRejectsMissingText() throws Exception {
        var response = post("/api/data", "{}", adminToken());

        assertEquals(400, response.statusCode());
    }

    @Test
    void createDataRejectsTooLongText() throws Exception {
        String body = JSON.writeValueAsString(new DataRequest("a".repeat(1001)));

        var response = post("/api/data", body, adminToken());

        assertEquals(400, response.statusCode());
    }

    private String adminToken() {
        return jwt.generateToken("admin");
    }

    private HttpResponse<String> getData(String token) throws Exception {
        return send("GET", "/api/data", "", token);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send("POST", path, body, token);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record DataRequest(String text) {
    }
}
