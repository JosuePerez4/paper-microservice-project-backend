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
import org.springframework.web.multipart.MultipartFile;

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
     * Subir archivo
     */
    public String uploadFile(MultipartFile file) {
        try {
            String fileName = generateFileName(file.getOriginalFilename());
            
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            s3Client.putObject(
                bucketName,
                fileName,
                file.getInputStream(),
                metadata
            );

            return fileName; // Retorno el fileName real para guardarlo en la base de datos
            
        } catch (IOException e) {
            throw new RuntimeException("Error al subir archivo", e);
        }
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
     * Eliminar archivo
     */
    public void deleteFile(String fileName) {
        s3Client.deleteObject(bucketName, fileName);
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

    /**
     * Generar nombre único para el archivo
     */
    private String generateFileName(String originalFileName) {
        return UUID.randomUUID().toString() + "_" + originalFileName;
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
