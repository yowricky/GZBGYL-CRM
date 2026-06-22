package com.gzbgyl.crm.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.attachment.application.StorageException;
import com.gzbgyl.crm.attachment.infrastructure.MinioStorageAdapter;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioStorageAdapterTest {
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "crm-attachments-test";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server /data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static MinioStorageAdapter adapter;

    @BeforeAll
    static void provisionBucket() throws Exception {
        String endpoint = endpoint();
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        adapter = new MinioStorageAdapter(endpoint, BUCKET, ACCESS_KEY, SECRET_KEY);
    }

    @Test
    void putGetAndDeleteRoundTripAgainstRealMinio() throws Exception {
        byte[] bytes = "hello minio".getBytes(StandardCharsets.UTF_8);

        adapter.put("objects/one", "text/plain", bytes.length, new ByteArrayInputStream(bytes));
        var stored = adapter.get("objects/one");

        assertThat(stored.contentType()).isEqualTo("text/plain");
        assertThat(stored.size()).isEqualTo(bytes.length);
        assertThat(stored.stream().readAllBytes()).isEqualTo(bytes);

        adapter.delete("objects/one");
        assertThatThrownBy(() -> adapter.get("objects/one"))
                .isInstanceOf(StorageException.class)
                .satisfies(exception -> assertThat(((StorageException) exception).isNotFound()).isTrue());
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }
}
