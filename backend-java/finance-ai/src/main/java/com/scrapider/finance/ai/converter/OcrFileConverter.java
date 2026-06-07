package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;

public final class OcrFileConverter {

    private OcrFileConverter() {
    }

    public static StoredOcrFileDTO storedFile(String bucket, String objectKey, String storedFilename) {
        return new StoredOcrFileDTO(
                "minio",
                bucket,
                objectKey,
                "minio://" + bucket + "/" + objectKey,
                storedFilename);
    }
}
