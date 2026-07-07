package com.example.noltok.global.exception;

import com.example.noltok.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 우리가 직접 던지는 비즈니스 예외 (예: 이메일 중복)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.fail(e.getMessage()));
    }

    // @Valid 검증 실패 (SignUpRequest의 @NotBlank, @Email 등)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("입력값 검증에 실패했습니다.");
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    // 업로드 파일이 spring.servlet.multipart.max-file-size를 초과한 경우
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("파일 크기가 너무 큽니다."));
    }

    // 예상치 못한 모든 예외 (DB 연결 끊김 등 진짜 장애 상황)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
