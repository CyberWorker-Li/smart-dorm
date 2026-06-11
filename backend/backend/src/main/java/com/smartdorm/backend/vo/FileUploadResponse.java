package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class FileUploadResponse {
    private String url;
    private String filename;
    private Long size;
    private String contentType;
}

