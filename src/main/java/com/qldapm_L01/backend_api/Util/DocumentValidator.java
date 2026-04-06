package com.qldapm_L01.backend_api.Util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.APPLICATION_PDF_VALUE
    );

    @Value("${app.storage.max-file-size-bytes}")
    private long maxFileSizeBytes;

    public ValidatedDocument validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("File exceeds max size");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (originalName.isBlank()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only PDF uploads are allowed");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid content type");
        }

        byte[] header;
        try (InputStream is = file.getInputStream()) {
            header = is.readNBytes(8);
        }

        if (!isPdf(header)) {
            throw new IllegalArgumentException("File content is not a valid PDF");
        }

        return new ValidatedDocument(originalName, extension, contentType, file.getSize());
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new IllegalArgumentException("File must have an extension");
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46;
    }



    public record ValidatedDocument(
            String originalName,
            String extension,
            String contentType,
            long size
    ) {}
}

