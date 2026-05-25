package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinioOcrFileStorageServiceImpl implements OcrFileStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioOcrFileStorageServiceImpl(
            MinioClient minioClient,
            @Value("${finance.minio.ocr-bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public StoredOcrFileDTO saveOriginalFile(String taskNo, String fileType, MultipartFile file) {
        String storedFilename = taskNo + "." + fileType;
        LocalDate today = LocalDate.now();
        String objectKey = String.format(
                "original/%d/%02d/%02d/%s",
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                storedFilename);

        try {
            this.ensureBucketExists();
            try (InputStream inputStream = file.getInputStream()) {
                this.minioClient.putObject(PutObjectArgs.builder()
                        .bucket(this.bucket)
                        .object(objectKey)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("保存上传文件到对象存储失败", ex);
        }

        return new StoredOcrFileDTO(
                "minio",
                this.bucket,
                objectKey,
                "minio://" + this.bucket + "/" + objectKey,
                storedFilename);
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = this.minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(this.bucket)
                .build());
        if (!exists) {
            this.minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(this.bucket)
                    .build());
        }
    }
}
