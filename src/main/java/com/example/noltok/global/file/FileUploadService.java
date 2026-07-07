package com.example.noltok.global.file;

import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.global.file.dto.response.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileStorageService fileStorageService;

    // 채팅 첨부파일 용도로 실제 필요한 확장자만 허용 (화이트리스트)
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "txt"
    );

    public FileUploadResponse upload(MultipartFile file) {
        // 1. 빈 파일인지 확인
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }

        // 2. 확장자 허용 목록 확인
        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        // 3. 실제 업로드는 FileStorageService에 위임
        String url = fileStorageService.upload(file);
        return FileUploadResponse.of(url);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
    }
}
