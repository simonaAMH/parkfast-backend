package com.example.licenta.Services;

import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Exceptions.InvalidDataException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

@Service
public class ImageService {
    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    public String saveImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        ensureUploadDirExists();

        String fileExtension = getFileExtension(file.getOriginalFilename());
        String uniqueFileName = UUID.randomUUID() + "." + fileExtension;

        Path filePath = Paths.get(uploadDir, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return just the path without any URL prefix
        return "/uploads/" + uniqueFileName;
    }

    public String saveImage(String imageData) throws IOException {
        if (imageData == null || imageData.isEmpty()) {
            return null;
        }

        // Return existing URLs as-is
        if (imageData.startsWith("http://") || imageData.startsWith("https://")) {
            return imageData;
        }

        ensureUploadDirExists();

        if (imageData.startsWith("data:image")) {
            String base64Content = imageData.split(",")[1];
            byte[] decodedBytes = Base64.getDecoder().decode(base64Content);

            String uniqueFileName = UUID.randomUUID() + ".jpg";
            Path filePath = Paths.get(uploadDir, uniqueFileName);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(decodedBytes);
            }

            // Return just the path without any URL prefix
            return "/uploads/" + uniqueFileName;
        }

        throw new IOException("Invalid image data format");
    }

    public boolean deleteImage(String imagePath) throws IOException {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            logger.warn("Image path is null or empty.");
            return false;
        }

        String filename = imagePath;
        if (imagePath.contains("/")) {
            filename = imagePath.substring(imagePath.lastIndexOf("/") + 1);
        }

        Path filePath = Paths.get(uploadDir, filename);
        logger.info("Attempting to delete file: {}", filePath);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            logger.info("Deleted file: {}", filePath);
            return true;
        } else {
            logger.warn("File not found for deletion: {}", filePath);
            return true;
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty() || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private void ensureUploadDirExists() throws IOException {
        Path path = Paths.get(uploadDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    public Resource loadAsResource(String filename) {
        try {
            Path fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = fileStorageLocation.resolve(filename).normalize();
            logger.debug("Loading file: {}", filePath);

            if (!Files.exists(filePath)) {
                throw new ResourceNotFoundException("File not found: " + filename);
            }

            if (!filePath.toAbsolutePath().startsWith(fileStorageLocation.toAbsolutePath())) {
                throw new InvalidDataException("File is outside of allowed upload directory");
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Could not read file: " + filename);
            }
        } catch (MalformedURLException ex) {
            throw new InvalidDataException("Malformed URL for file " + filename);
        }
    }
}