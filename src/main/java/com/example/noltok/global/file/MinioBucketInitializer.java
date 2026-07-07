package com.example.noltok.global.file;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// MinioConfig와 분리한 이유: @PostConstruct 안에서 같은 클래스의 @Bean 메서드를
// 직접 호출하면 "빈이 자기 자신을 참조"하는 순환 참조로 취급되어 기동에 실패함
// → MinioClient를 생성자로 정상 주입받는 별도 컴포넌트로 분리
@Component
@RequiredArgsConstructor
public class MinioBucketInitializer {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    // 버킷이 없으면 생성하고, 업로드된 파일을 URL만으로 누구나 내려받을 수 있게
    // 다운로드(GetObject)만 공개하는 정책을 적용 (업로드는 여전히 인증 필요)
    @PostConstruct
    public void initBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        String publicReadPolicy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);

        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(publicReadPolicy)
                .build());
    }
}
