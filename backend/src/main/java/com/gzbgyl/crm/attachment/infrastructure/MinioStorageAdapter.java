package com.gzbgyl.crm.attachment.infrastructure;

import com.gzbgyl.crm.attachment.application.ObjectStorage;
import com.gzbgyl.crm.attachment.application.StoredObject;
import com.gzbgyl.crm.attachment.application.StorageException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("app.minio.endpoint")
public class MinioStorageAdapter implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    @Autowired
    public MinioStorageAdapter(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.bucket}") String bucket,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey) {
        this(MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(), bucket);
    }

    MinioStorageAdapter(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, String contentType, long size, InputStream in) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .contentType(contentType)
                    .stream(in, size, -1)
                    .build());
        } catch (Exception exception) {
            throw storageException("Object upload failed", exception);
        }
    }

    @Override
    public StoredObject get(String key) {
        try {
            var stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            InputStream stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
            return new StoredObject(stat.contentType(), stat.size(), stream);
        } catch (Exception exception) {
            throw storageException("Object download failed", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw storageException("Object delete failed", exception);
        }
    }

    private static StorageException storageException(String message, Exception exception) {
        if (exception instanceof ErrorResponseException error
                && ("NoSuchKey".equals(error.errorResponse().code())
                || "NoSuchObject".equals(error.errorResponse().code()))) {
            return StorageException.notFound("Object not found");
        }
        return new StorageException(message, exception);
    }
}
