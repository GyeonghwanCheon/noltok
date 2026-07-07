package com.example.noltok.global.file.dto.response;

public record FileUploadResponse(
        String url
) {
    public static FileUploadResponse of(String url) {
        return new FileUploadResponse(url);
    }
}
