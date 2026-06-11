package com.smartdorm.backend.service;

import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.vo.FileUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public FileUploadResponse storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 JPG/PNG/WEBP/GIF 图片");
        }
        String ext = guessExt(contentType, file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(filename).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new BusinessException("非法文件路径");
            }
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException("保存文件失败");
        }

        FileUploadResponse resp = new FileUploadResponse();
        resp.setUrl("/uploads/" + filename);
        resp.setFilename(filename);
        resp.setSize(file.getSize());
        resp.setContentType(contentType);
        return resp;
    }

    private String guessExt(String contentType, String originalFilename) {
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("jpeg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("gif")) return ".gif";

        if (originalFilename != null) {
            int idx = originalFilename.lastIndexOf('.');
            if (idx >= 0 && idx < originalFilename.length() - 1) {
                String ext = originalFilename.substring(idx).toLowerCase(Locale.ROOT);
                if (ext.length() <= 10) return ext;
            }
        }
        return "";
    }
}

