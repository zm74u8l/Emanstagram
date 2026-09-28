package com.emanstagram.storage;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Media on any S3-compatible store. Built for Cloudflare R2, which charges
 * nothing for egress, but equally happy with MinIO, Backblaze B2 or AWS.
 *
 * <p>Two buckets: a public one holding {@code posts/}, {@code avatars/} and
 * {@code stories/} (served straight from {@code publicBaseUrl}, so image
 * traffic never touches this server), and a private one holding
 * {@code messages/}, reachable only through presigned URLs. Presigning is a
 * local HMAC computation, so unlike Supabase it costs no network round trip.
 */
@Service
@ConditionalOnProperty(name = "emanstagram.storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    /** DeleteObjects accepts at most this many keys per request. */
    private static final int DELETE_BATCH = 1000;

    private final EmanstagramProperties.Storage.S3 config;
    private final boolean configured;
    private final String publicBaseUrl;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageService(EmanstagramProperties properties) {
        this.config = properties.storage().s3();
        this.configured = isConfigured(config);
        this.publicBaseUrl = configured ? config.publicBaseUrl().replaceAll("/+$", "") : "";

        if (!configured) {
            log.warn("STORAGE_PROVIDER=s3 but the S3_* settings are incomplete. Media endpoints return "
                    + "a clear error until S3_ENDPOINT, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY, "
                    + "S3_PUBLIC_BUCKET, S3_PRIVATE_BUCKET and S3_PUBLIC_BASE_URL are set.");
            this.client = null;
            this.presigner = null;
            return;
        }

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey()));
        var region = Region.of(config.region() == null || config.region().isBlank() ? "auto" : config.region());
        var endpoint = URI.create(config.endpoint());
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(config.pathStyle()).build();

        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                // Recent SDKs add CRC checksums to every request by default.
                // Not every S3-compatible store accepts them, so only send
                // them where the S3 API itself requires one.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(10))
                        .socketTimeout(Duration.ofSeconds(120))
                        .build())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();

        log.info("Media storage: S3-compatible at {} (public bucket {}, private bucket {})",
                endpoint.getHost(), config.publicBucket(), config.privateBucket());
    }

    private static boolean isConfigured(EmanstagramProperties.Storage.S3 c) {
        return c != null
                && notBlank(c.endpoint()) && notBlank(c.accessKeyId()) && notBlank(c.secretAccessKey())
                && notBlank(c.publicBucket()) && notBlank(c.privateBucket()) && notBlank(c.publicBaseUrl());
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
            presigner.close();
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || !configured) {
            return null;
        }
        return publicBaseUrl + "/" + encodePath(storageKey);
    }

    @Override
    public Map<String, String> signedUrls(Collection<String> storageKeys, Duration ttl) {
        Map<String, String> result = new HashMap<>();
        if (!configured || storageKeys == null) {
            return result;
        }
        for (String key : new LinkedHashSet<>(storageKeys)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (!isPrivate(key)) {
                result.put(key, publicUrl(key));
                continue;
            }
            try {
                String url = presigner.presignGetObject(r -> r
                        .signatureDuration(ttl)
                        .getObjectRequest(g -> g.bucket(config.privateBucket()).key(key)))
                        .url().toString();
                result.put(key, url);
            } catch (RuntimeException ex) {
                log.warn("Failed to presign {}: {}", key, ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public String upload(String kind, UUID owner, byte[] data, String contentType, String extension) {
        requireConfigured();
        String key = StorageService.newKey(kind, owner, extension);
        String bucket = bucketFor(key);
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            // Keys are never reused, so browsers and CDNs may cache forever.
                            .cacheControl(isPrivate(key) ? "private, max-age=3600" : "public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(data));
            return key;
        } catch (RuntimeException ex) {
            log.error("S3 upload failed for {}/{}", bucket, key, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "UPLOAD_FAILED",
                    "The upload could not be completed. Please try again.");
        }
    }

    @Override
    public void deleteAll(Collection<String> storageKeys) {
        if (!configured || storageKeys == null || storageKeys.isEmpty()) {
            return;
        }
        Map<String, List<ObjectIdentifier>> byBucket = new HashMap<>();
        for (String key : storageKeys) {
            if (key != null && !key.isBlank()) {
                byBucket.computeIfAbsent(bucketFor(key), b -> new ArrayList<>())
                        .add(ObjectIdentifier.builder().key(key).build());
            }
        }
        byBucket.forEach((bucket, ids) -> {
            for (int i = 0; i < ids.size(); i += DELETE_BATCH) {
                List<ObjectIdentifier> batch = ids.subList(i, Math.min(ids.size(), i + DELETE_BATCH));
                try {
                    client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(Delete.builder().objects(batch).quiet(true).build())
                            .build());
                } catch (RuntimeException ex) {
                    log.warn("Failed to delete {} object(s) from {}: {}", batch.size(), bucket, ex.getMessage());
                }
            }
        });
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void requireConfigured() {
        if (!configured) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
                    "Media storage is not configured on this server yet.");
        }
    }

    // ---------------- helpers ----------------

    private static boolean isPrivate(String key) {
        return MESSAGES.equals(StorageService.kindOf(key));
    }

    private String bucketFor(String key) {
        return isPrivate(key) ? config.privateBucket() : config.publicBucket();
    }

    /** URL-encodes each path segment, keeping the slashes. */
    static String encodePath(String path) {
        StringJoiner joiner = new StringJoiner("/");
        for (String segment : path.split("/")) {
            joiner.add(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return joiner.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
