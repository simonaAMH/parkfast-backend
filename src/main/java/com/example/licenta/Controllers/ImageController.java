package com.example.licenta.Controllers;

import com.example.licenta.DTOs.ApiResponse;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Exceptions.FileProcessingException;
import com.example.licenta.Services.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDataException("Please select a file to upload");
        }

        if (!isValidImageType(file)) {
            throw new InvalidDataException("Only JPG, JPEG and PNG files are allowed");
        }

        try {
            String imagePath = imageService.saveImage(file);
            if (imagePath.contains("/uploads/")) {
                String filename = imagePath.substring(imagePath.lastIndexOf("/") + 1);
                imagePath = "/api/uploads/" + filename;
            }

            Map<String, String> data = new HashMap<>();
            data.put("url", imagePath);

            ApiResponse<Map<String, String>> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "Image uploaded successfully",
                    data
            );

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to upload image: " + e.getMessage());
        }
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        if (!hasAllowedImageExtension(filename)) {
            throw new InvalidDataException("Unsupported media type. Only JPG, JPEG and PNG files are allowed");
        }

        Resource file = imageService.loadAsResource(filename);

        String contentType = determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(file);
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidDataException("Filename is required");
        }

        try {
            imageService.deleteImage(filename);

            ApiResponse<Void> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "Image deleted successfully",
                    null
            );

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Image not found or could not be deleted: " + filename);
        }
    }

    private boolean hasAllowedImageExtension(String filename) {
        String lowercaseName = filename.toLowerCase();
        return lowercaseName.endsWith(".jpg") ||
                lowercaseName.endsWith(".jpeg") ||
                lowercaseName.endsWith(".png");
    }

    private boolean isValidImageType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                        contentType.equals("image/jpg") ||
                        contentType.equals("image/png"));
    }

    private String determineContentType(String filename) {
        String lowercaseName = filename.toLowerCase();

        if (lowercaseName.endsWith(".png")) {
            return "image/png";
        } else if (lowercaseName.endsWith(".jpg") || lowercaseName.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "image/jpeg";
    }
}