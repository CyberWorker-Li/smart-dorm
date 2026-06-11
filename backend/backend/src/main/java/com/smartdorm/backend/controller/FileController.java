package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.service.FileStorageService;
import com.smartdorm.backend.vo.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<FileUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return Result.success(fileStorageService.storeImage(file));
    }
}

