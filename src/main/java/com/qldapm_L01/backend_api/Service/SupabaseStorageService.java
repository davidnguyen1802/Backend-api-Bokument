package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Config.SupabaseStorageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    private final RestTemplate restTemplate;
    private final SupabaseStorageConfig config;

    /**
     * Upload a file to Supabase Storage.
     *
     * @param path        the storage path inside the bucket (e.g. "documents/1/uuid_file.pdf")
     * @param fileContent the file bytes
     * @param contentType the MIME type of the file
     */
    public void uploadFile(String path, byte[] fileContent, String contentType) {
        String url = String.format("%s/storage/v1/object/%s/%s",
                config.getUrl(), config.getBucketName(), path);

        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));

        HttpEntity<byte[]> entity = new HttpEntity<>(fileContent, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload file to Supabase Storage: " + response.getBody());
        }
    }

    /**
     * Download a file from Supabase Storage.
     *
     * @param path the storage path inside the bucket
     * @return the file bytes
     */
    public byte[] downloadFile(String path) {
        String url = String.format("%s/storage/v1/object/%s/%s",
                config.getUrl(), config.getBucketName(), path);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to download file from Supabase Storage");
        }

        return response.getBody();
    }

    /**
     * Delete a file from Supabase Storage.
     *
     * @param path the storage path inside the bucket
     */
    public void deleteFile(String path) {
        String url = String.format("%s/storage/v1/object/%s/%s",
                config.getUrl(), config.getBucketName(), path);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to delete file from Supabase Storage: " + response.getBody());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getServiceRoleKey());
        headers.set("apikey", config.getServiceRoleKey());
        return headers;
    }
}

