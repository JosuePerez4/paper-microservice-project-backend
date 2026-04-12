package microservice.service.paper.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reduce el tamaño de PDF e imágenes comunes antes de subirlos al almacenamiento.
 * Otros tipos se suben sin cambios.
 */
@Component
public class FileOptimizer {

    private static final float JPEG_QUALITY = 0.82f;

    public OptimizedPayload optimize(MultipartFile file) throws IOException {
        byte[] original = file.getBytes();
        String ct = file.getContentType();
        if (ct == null) {
            ct = guessContentType(file.getOriginalFilename());
        }

        if ("application/pdf".equalsIgnoreCase(ct)) {
            return tryOptimizePdf(original);
        }
        if ("image/jpeg".equalsIgnoreCase(ct) || "image/jpg".equalsIgnoreCase(ct)) {
            return tryOptimizeJpeg(original);
        }
        if ("image/png".equalsIgnoreCase(ct)) {
            return tryOptimizePng(original);
        }

        return new OptimizedPayload(original, ct, original.length);
    }

    private static String guessContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    private OptimizedPayload tryOptimizePdf(byte[] original) {
        try (PDDocument doc = Loader.loadPDF(original)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            byte[] out = baos.toByteArray();
            if (out.length < original.length) {
                return new OptimizedPayload(out, "application/pdf", out.length);
            }
        } catch (IOException ignored) {
            // PDF ilegible o protegido: usar bytes originales
        }
        return new OptimizedPayload(original, "application/pdf", original.length);
    }

    private OptimizedPayload tryOptimizeJpeg(byte[] original) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
        if (img == null) {
            return new OptimizedPayload(original, "image/jpeg", original.length);
        }
        BufferedImage rgb = convertToRgb(img);
        byte[] compressed = writeJpeg(rgb, JPEG_QUALITY);
        if (compressed.length < original.length) {
            return new OptimizedPayload(compressed, "image/jpeg", compressed.length);
        }
        return new OptimizedPayload(original, "image/jpeg", original.length);
    }

    private OptimizedPayload tryOptimizePng(byte[] original) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
        if (img == null) {
            return new OptimizedPayload(original, "image/png", original.length);
        }
        if (!img.getColorModel().hasAlpha()) {
            BufferedImage rgb = convertToRgb(img);
            byte[] jpegBytes = writeJpeg(rgb, JPEG_QUALITY);
            if (jpegBytes.length < original.length) {
                return new OptimizedPayload(jpegBytes, "image/jpeg", jpegBytes.length);
            }
        }
        return new OptimizedPayload(original, "image/png", original.length);
    }

    private static BufferedImage convertToRgb(BufferedImage src) {
        int type = src.getType();
        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_3BYTE_BGR) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setBackground(Color.WHITE);
            g.clearRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] writeJpeg(BufferedImage img, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No hay escritor JPEG disponible");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    public record OptimizedPayload(byte[] data, String contentType, int size) {}
}
