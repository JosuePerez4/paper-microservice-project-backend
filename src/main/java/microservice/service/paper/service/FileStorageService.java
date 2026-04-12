package microservice.service.paper.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileStorageService {

    @Autowired
    private AmazonS3 s3Client;

    @Value("${backblaze.bucket-name}")
    private String bucketName;

    /**
     * Sube bytes ya optimizados a Backblaze B2 (API compatible S3).
     */
    public String uploadOptimized(byte[] data, String contentType, String originalFileName) {
        String fileName = generateFileName(originalFileName, contentType);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType != null ? contentType : "application/octet-stream");
        metadata.setContentLength(data.length);

        s3Client.putObject(
            bucketName,
            fileName,
            new ByteArrayInputStream(data),
            metadata
        );

        return fileName;
    }

    /**
     * Descargar archivo
     */
    public byte[] downloadFile(String fileName) {
        S3Object s3Object = s3Client.getObject(bucketName, fileName);
        try (S3ObjectInputStream inputStream = s3Object.getObjectContent()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error al descargar archivo", e);
        }
    }

    /**
     * Elimina el objeto en el bucket B2. Requiere acceso path-style en el cliente S3.
     */
    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("La clave del objeto en almacenamiento no puede estar vacía");
        }
        s3Client.deleteObject(bucketName, objectKey.trim());
    }

    /**
     * Obtener URL pública del archivo
     */
    public String getFileUrl(String fileName) {
        return String.format("https://%s/%s/%s", 
            s3Client.getUrl(bucketName, fileName).getHost(),
            bucketName,
            fileName
        );
    }

    private String generateFileName(String originalFileName, String contentType) {
        String ext = extensionFromFilename(originalFileName);
        String fromMime = extensionFromContentType(contentType);
        if (fromMime != null && (ext.isEmpty() || incompatibleExtension(ext, contentType))) {
            ext = fromMime;
        }
        return UUID.randomUUID() + ext;
    }

    private static boolean incompatibleExtension(String ext, String contentType) {
        String mime = contentType != null ? contentType.toLowerCase() : "";
        if (mime.startsWith("image/jpeg") && !ext.matches("(?i)\\.(jpe?g)")) {
            return true;
        }
        if ("image/png".equals(mime) && !ext.equalsIgnoreCase(".png")) {
            return true;
        }
        if ("application/pdf".equals(mime) && !ext.equalsIgnoreCase(".pdf")) {
            return true;
        }
        return false;
    }

    private static String extensionFromFilename(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf('.'));
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            default -> null;
        };
    }

    /**
     * Listar archivos
     */
    public List<String> listFiles() {
        ObjectListing objectListing = s3Client.listObjects(bucketName);
        return objectListing.getObjectSummaries()
            .stream()
            .map(S3ObjectSummary::getKey)
            .collect(Collectors.toList());
    }
}
