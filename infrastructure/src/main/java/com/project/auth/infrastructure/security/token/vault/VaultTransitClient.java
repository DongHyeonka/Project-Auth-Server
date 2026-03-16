package com.project.auth.infrastructure.security.token.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

public class VaultTransitClient {

    private final String address;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public VaultTransitClient(
            String address,
            String token,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.address = normalizeAddress(address);
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public VaultTransitKeyMetadata readKey(String mountPath, String transitKeyName) {
        JsonNode data = execute(
                HttpRequest.newBuilder(buildUri("/v1/" + mountPath + "/keys/" + transitKeyName))
                        .GET()
                        .header("X-Vault-Token", token)
                        .build()
        ).path("data");

        if (!data.path("supports_signing").asBoolean(false)) {
            throw new IllegalStateException("Configured Vault transit key does not support signing.");
        }

        int latestVersion = data.path("latest_version").asInt();
        String publicKey = data.path("keys")
                .path(String.valueOf(latestVersion))
                .path("public_key")
                .asText();

        if (latestVersion <= 0 || publicKey.isBlank()) {
            throw new IllegalStateException("Vault transit key metadata does not contain a usable public key.");
        }

        return new VaultTransitKeyMetadata(latestVersion, publicKey);
    }

    public String sign(String mountPath, String transitKeyName, byte[] input) {
        String requestBody = writeRequestBody(Map.of(
                "input", Base64.getEncoder().encodeToString(input),
                "signature_algorithm", "pkcs1v15",
                "hash_algorithm", "sha2-256"
        ));

        JsonNode data = execute(
                HttpRequest.newBuilder(buildUri("/v1/" + mountPath + "/sign/" + transitKeyName))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .header("X-Vault-Token", token)
                        .header("Content-Type", "application/json")
                        .build()
        ).path("data");

        String signature = data.path("signature").asText();
        if (signature.isBlank()) {
            throw new IllegalStateException("Vault transit signing response did not contain a signature.");
        }

        return signature;
    }

    private JsonNode execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Vault transit request failed with status " + response.statusCode() + "."
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("errors") && root.path("errors").isArray() && !root.path("errors").isEmpty()) {
                throw new IllegalStateException("Vault transit request failed: " + root.path("errors"));
            }

            return root;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to call Vault transit API.", exception);
        }
    }

    private String writeRequestBody(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize Vault transit request body.", exception);
        }
    }

    private URI buildUri(String path) {
        return URI.create(address + path);
    }

    private static String normalizeAddress(String address) {
        Objects.requireNonNull(address, "address must not be null");
        return address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
    }
}
