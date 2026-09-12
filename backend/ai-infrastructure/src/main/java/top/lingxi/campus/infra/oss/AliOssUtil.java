package top.lingxi.campus.infra.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Slf4j
@Component
@RequiredArgsConstructor
public class AliOssUtil {


    private final AliOssProperties aliOssProperties;

    /**
     * 文件上传（兼容旧调用，默认存根目录）
     */
    public String upload(byte[] bytes, String objectName) {
        return upload(bytes, "", objectName);
    }

    /**
     * 文件上传（支持目录分类）
     *
     * @param bytes            文件字节数组
     * @param dirPrefix        目录前缀，如 "astra/1" 或 "avatar/2026/07"
     * @param originalFileName 原始文件名，用于提取后缀
     * @return 完整访问 URL，如 <a href="https://bucket.endpoint/astra/1/uuid.txt">...</a>
     */
    public String upload(byte[] bytes, String dirPrefix, String originalFileName) {
        // 安全提取后缀（无后缀则空字符串，不会崩）
        int dotIndex = originalFileName.lastIndexOf(".");
        String extension = (dotIndex == -1) ? "" : originalFileName.substring(dotIndex);

        // 规范化目录前缀：有内容时确保以 / 结尾
        if (dirPrefix == null) {
            dirPrefix = "";
        } else if (!dirPrefix.isEmpty() && !dirPrefix.endsWith("/")) {
            dirPrefix = dirPrefix + "/";
        }

        // 生成带目录的唯一文件名
        String objectName = dirPrefix + UUID.randomUUID().toString() + extension;

        // 获取配置
        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();

        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        } catch (OSSException oe) {
            log.error("OSS异常 - ErrorCode: {}, Message: {}, RequestId: {}",
                    oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId());
            throw new RuntimeException("文件上传失败", oe);
        } catch (ClientException ce) {
            log.error("OSS客户端异常 - Message: {}", ce.getMessage());
            throw new RuntimeException("文件上传失败", ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        // 拼接文件访问路径
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder.append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(objectName);

        log.info("文件上传到: {}", stringBuilder);
        return stringBuilder.toString();
    }


    /**
     * 文件上传并【返回临时签名URL】（有效期120分钟）
     *
     * @param bytes       文件字节数组
     * @param objectName  原始文件名（用于提取后缀）
     * @return 临时可访问的签名URL（例如：<a href="https://xxx?Expires=...&OSSAccessKeyId=...&Signature=">...</a>...）
     */
    public String upload2(byte[] bytes, String objectName) {
        return upload2(bytes, "", objectName);
    }

    /**
     * 文件上传并返回临时签名URL（支持目录分类）
     */
    public String upload2(byte[] bytes, String dirPrefix, String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf(".");
        String extension = (dotIndex == -1) ? "" : originalFileName.substring(dotIndex);

        if (dirPrefix == null) {
            dirPrefix = "";
        } else if (!dirPrefix.isEmpty() && !dirPrefix.endsWith("/")) {
            dirPrefix = dirPrefix + "/";
        }

        String uniqueObjectName = dirPrefix + UUID.randomUUID().toString() + extension;

        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();

        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            ossClient.putObject(bucketName, uniqueObjectName, new ByteArrayInputStream(bytes));

            Date expiration = new Date(System.currentTimeMillis() + 120 * 60 * 1000);
            URL signedUrl = ossClient.generatePresignedUrl(bucketName, uniqueObjectName, expiration);

            log.info("文件上传成功，临时访问地址: {}", signedUrl);
            return signedUrl.toString();

        } catch (OSSException oe) {
            log.error("OSS异常 - 错误码: {}, 消息: {}, RequestId: {}",
                    oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId());
            throw new RuntimeException("文件上传失败", oe);
        } catch (ClientException ce) {
            log.error("客户端异常 - 消息: {}", ce.getMessage());
            throw new RuntimeException("文件上传失败", ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 后端签发临时访问凭证给前端，用于前端直传OSS
     * @param userId
     * @return
     * @throws ClientException
     */
    public Map<String, String> getTempCredentials(Long userId) throws com.aliyuncs.exceptions.ClientException {
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String roleArn = aliOssProperties.getRoleArn();
        String region = aliOssProperties.getRegion();
        String roleSessionName = aliOssProperties.getRoleSessionName();
        // 1. 初始化客户端
        DefaultProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
        DefaultAcsClient client = new DefaultAcsClient(profile);

        // 2. 构造请求
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setMethod(MethodType.POST);
        request.setRoleArn(roleArn);
        request.setRoleSessionName(roleSessionName + "-" + userId);
        request.setDurationSeconds(900L); // 临时凭证有效期：15分钟 (此api最小允许15min，最大允许1h)

        // 3. 限制上传路径（只能上传到自己的目录）
        String policy = String.format("""
            {
              "Version": "1",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": ["oss:PutObject", "oss:PostObject"],
                  "Resource": "acs:oss:*:*:%s/music/%d/*"
                }
              ]
            }
            """,
                aliOssProperties.getBucketName(),
                userId
        );
        request.setPolicy(policy);

        // 4. 获取临时凭证
        AssumeRoleResponse response = client.getAcsResponse(request);

        Map<String, String> result = new HashMap<>();
        result.put("accessKeyId", response.getCredentials().getAccessKeyId());
        result.put("accessKeySecret", response.getCredentials().getAccessKeySecret());
        result.put("securityToken", response.getCredentials().getSecurityToken());
        result.put("bucket", aliOssProperties.getBucketName());
        result.put("region", region);
        result.put("endpoint", aliOssProperties.getEndpoint());
        result.put("dir", "music/" + userId + "/");
        return result;
    }

    /**
     * 删除 OSS 文件
     * @param objectKey OSS 对象 key（UUID-based），从完整 URL 中提取
     */
    public void delete(String objectKey) {
        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, objectKey);
            log.info("OSS 文件删除成功: {}", objectKey);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 从完整 URL 中提取 objectKey
     * 例如: https://bucket.endpoint/astra/1/sha256 -> astra/1/sha256
     */
    public String extractObjectKey(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) {
            return null;
        }
        int idx = fullUrl.indexOf("/", 8); // skip "https://"
        if (idx > 0) {
            return fullUrl.substring(idx + 1);
        }
        return fullUrl;
    }

    /**
     * 下载 OSS 文件到字节数组
     * @param fullUrl 完整的文件访问 URL
     * @return 文件字节数组
     */
    public byte[] download(String fullUrl) throws IOException {
        String objectKey = extractObjectKey(fullUrl);
        if (objectKey == null) {
            throw new RuntimeException("无法从 URL 中提取 objectKey: " + fullUrl);
        }

        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            byte[] buffer = ossObject.getObjectContent().readAllBytes();
            log.info("OSS 文件下载成功: objectKey={}, size={}", objectKey, buffer.length);
            return buffer;
        } finally {
            ossClient.shutdown();
        }
    }
}