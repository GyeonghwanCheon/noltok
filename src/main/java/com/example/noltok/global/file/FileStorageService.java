package com.example.noltok.global.file;

import org.springframework.web.multipart.MultipartFile;

// 구현체(MinIO/S3 등)를 교체해도 이 인터페이스를 쓰는 코드는 변경 없음
public interface FileStorageService {

    // 업로드 성공 시 접근 가능한 URL을 반환
    String upload(MultipartFile file);
}
