package com.project.auth.infrastructure.security.token.vault;

import com.project.auth.infrastructure.support.exception.InfrastructureErrorCode;
import com.project.auth.infrastructure.support.exception.InfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class VaultTransitClient {

    private static final Logger log = LoggerFactory.getLogger(VaultTransitClient.class);
    private static final String API_VERSION = "v1";
    private static final String KEYS_PATH = "keys";
    private static final String SIGN_PATH = "sign";
    private static final String SIGNATURE_ALGORITHM = "pkcs1v15";
    private static final String HASH_ALGORITHM = "sha2-256";

    private final RestClient restClient;

    public VaultTransitClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public VaultTransitKeyMetadata readKey(String mountPath, String transitKeyName) {
        VaultTransitKeyResponse response = executeGet(
                buildPath(mountPath, KEYS_PATH, transitKeyName),
                VaultTransitKeyResponse.class
        );
        assertNoVaultErrors(response.errors());

        VaultTransitKeyResponse.VaultTransitKeyData data = response.data();
        if (data == null) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Vault transit key response did not contain data.");
        }

        if (!data.supportsSigning()) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Configured Vault transit key does not support signing.");
        }

        int latestVersion = data.latestVersion();
        VaultTransitKeyResponse.VaultTransitKeyVersion latestKey = data.keys() == null
                ? null
                : data.keys().get(String.valueOf(latestVersion));
        String publicKey = latestKey == null ? null : latestKey.publicKey();

        if (latestVersion <= 0 || publicKey == null || publicKey.isBlank()) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Vault transit key metadata does not contain a usable public key.");
        }

        log.info("Vault transit key metadata loaded: keyName={}, version={}", transitKeyName, latestVersion);
        return new VaultTransitKeyMetadata(latestVersion, publicKey);
    }

    public String sign(String mountPath, String transitKeyName, byte[] input) {
        VaultTransitSignRequest requestBody = new VaultTransitSignRequest(
                Base64.getEncoder().encodeToString(input),
                SIGNATURE_ALGORITHM,
                HASH_ALGORITHM
        );

        VaultTransitSignResponse response = executePost(
                buildPath(mountPath, SIGN_PATH, transitKeyName),
                requestBody,
                VaultTransitSignResponse.class
        );
        assertNoVaultErrors(response.errors());

        String signature = response.data() == null ? null : response.data().signature();
        if (signature == null || signature.isBlank()) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Vault transit signing response did not contain a signature.");
        }

        return signature;
    }

    private <T> T executeGet(String path, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new InfrastructureException(
                                InfrastructureErrorCode.VAULT_TRANSIT_FAILED,
                                "Vault transit request failed with status " + response.getStatusCode().value() + "."
                        );
                    })
                    .body(responseType);
        } catch (InfrastructureException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Failed to call Vault transit API.", exception);
        }
    }

    private <T> T executePost(String path, Object requestBody, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new InfrastructureException(
                                InfrastructureErrorCode.VAULT_TRANSIT_FAILED,
                                "Vault transit request failed with status " + response.getStatusCode().value() + "."
                        );
                    })
                    .body(responseType);
        } catch (InfrastructureException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Failed to call Vault transit API.", exception);
        }
    }

    private void assertNoVaultErrors(List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Vault transit request failed: " + errors);
        }
    }

    private static String buildPath(String mountPath, String capabilityPath, String transitKeyName) {
        return "/" + String.join(
                "/",
                API_VERSION,
                normalizePathSegment(mountPath),
                capabilityPath,
                normalizePathSegment(transitKeyName)
        );
    }

    private static String normalizePathSegment(String segment) {
        Objects.requireNonNull(segment, "segment must not be null");
        String normalized = segment.strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            throw new InfrastructureException(InfrastructureErrorCode.VAULT_TRANSIT_FAILED, "Vault transit path segment is invalid.");
        }
        return normalized;
    }
}
