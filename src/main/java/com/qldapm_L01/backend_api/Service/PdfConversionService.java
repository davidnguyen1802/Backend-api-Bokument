package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfConversionService {

    private final S3Client s3Client;
    private final DocumentRepository repository;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Async
    public void processAndUploadImages(Document document, File tempPdfFile) {
        log.info("Starting PDF to Image conversion for document: {}", document.getId());
        document.setProcessingStatus("PENDING");
        repository.save(document);

        try (PDDocument pdf = PDDocument.load(tempPdfFile)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = pdf.getNumberOfPages();
            log.info("Document {} has {} pages", document.getId(), pageCount);

            for (int i = 0; i < pageCount; i++) {
                // Render at 150 DPI for good web reading quality but reasonable file size
                BufferedImage image = renderer.renderImageWithDPI(i, 150);

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", os);
                byte[] bytes = os.toByteArray();

                // pages are 1-indexed for the API
                String imgKey = "users/" + document.getOwnerId() + "/" + document.getId() + "/pages/page_" + (i + 1) + ".jpg";

                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(imgKey)
                        .contentType("image/jpeg")
                        .build();

                s3Client.putObject(request, RequestBody.fromBytes(bytes));
                log.debug("Uploaded page {} for document {}", (i + 1), document.getId());
            }

            document.setPageCount(pageCount);
            document.setProcessingStatus("READY");
            repository.save(document);
            log.info("Successfully converted PDF to {} images for document {}", pageCount, document.getId());

        } catch (Exception e) {
            log.error("Failed to convert PDF to images for document " + document.getId(), e);
            document.setProcessingStatus("FAILED");
            repository.save(document);
        } finally {
            if (tempPdfFile.exists()) {
                boolean deleted = tempPdfFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temporary PDF file: {}", tempPdfFile.getAbsolutePath());
                }
            }
        }
    }
}
