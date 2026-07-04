package com.minimall.file.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.file.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;


@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;
    @Autowired private MinioProperties props;

    // ── 加固用的三条硬规则(常量) ─────────────────────────────────────
    // 业务层大小上限 5MB。注意: Spring 的 multipart.max-file-size(10MB) 是"网关口"的粗限,
    //   这里是"业务口"的细限, 两层都要有(万一以后 yml 被改大, 业务这层仍兜底)。
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    // bizType 白名单: 决定一级目录, 只允许这几种, 防止有人传 "../" 之类乱造路径。
    private static final Set<String> ALLOWED_BIZ = Set.of("product", "avatar", "review", "category");

    /**
     * 允许上传的图片类型。每种都带"魔数(magic number)"——文件真正的头几个字节。
     * 核心安全思想: 不信任前端给的扩展名和 Content-Type(都能伪造),
     *              只认文件内容开头的魔数, 才知道它"真的是什么"。
     * 故意不收 SVG: SVG 是 XML, 能内嵌 <script>, 放到公共读的桶里等于挂了个 XSS 页面。
     */
    private enum ImageType {
        JPEG(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg", "jpg"),
        PNG(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, "image/png", "png"),   // 0x89 P N G
        GIF(new byte[]{0x47, 0x49, 0x46, 0x38}, "image/gif", "gif");          // G I F 8
        // ⭐ TODO(你练手): 加一个 WEBP。它的魔数不在开头, 是第 0~3 字节 "RIFF" + 第 8~11 字节 "WEBP",
        //    需要单独判断(不能只比开头), 试着扩展 detect() 支持它。

        final byte[] magic;         // 文件开头必须匹配的字节序列
        final String contentType;   // 服务端自己定的、可信的 Content-Type
        final String ext;           // 服务端自己定的规范扩展名(不用前端给的)

        ImageType(byte[] magic, String contentType, String ext) {
            this.magic = magic;
            this.contentType = contentType;
            this.ext = ext;
        }

        /** 判断给定的文件头 head 是不是以本类型的魔数开头。 */
        boolean matches(byte[] head) {
            if (head.length < magic.length) return false;   // 文件比魔数还短, 直接不匹配
            for (int i = 0; i < magic.length; i++) {
                if (head[i] != magic[i]) return false;       // 有一个字节对不上就不是
            }
            return true;
        }
    }

    /** 遍历所有允许类型, 返回文件内容真正命中的那个; 都不命中返回 null(=非法文件)。 */
    private ImageType detect(byte[] bytes) {
        for (ImageType type : ImageType.values()) {
            if (type.matches(bytes)) return type;
        }
        return null;
    }
    // 注释: 这就是"验魔数"的落地——拿文件真实内容去比对, 而不是看它叫什么名字

    /**
     * 上传文件(加固版)。
     * @param file    前端传来的文件
     * @param bizType 业务类型(product/avatar/...) 决定一级目录
     * @return 可访问的完整 URL
     */
    public String upload(MultipartFile file, String bizType) {
        // ① 空文件检查
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }

        // ② bizType 白名单检查: 不在名单里的一律拒绝(默认拒绝思想, 跟网关那套一致)
        if (bizType == null || !ALLOWED_BIZ.contains(bizType)) {
            throw new BusinessException(400, "非法的业务类型: " + bizType);
        }

        // ③ 大小检查: 超过 5MB 拒绝(业务层兜底, 不依赖 multipart 配置)
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "文件过大, 最大允许 5MB");
        }

        // ④ 把文件读进内存(已限 5MB, 安全)。后面既要用它验魔数, 又要用它上传, 读一次复用。
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(500, "读取文件失败: " + e.getMessage());
        }

        // ⑤ 核心: 验魔数, 判断"文件内容到底是不是我们允许的图片"。
        //    这一步让"把 evil.html 改名成 a.png 再传"彻底失效——因为内容开头不是图片魔数。
        ImageType type = detect(bytes);
        if (type == null) {
            throw new BusinessException(400, "不支持的文件类型: 仅允许 jpg/png/gif 图片");
        }

        // ⑥ 用"服务端认定的"规范扩展名和 Content-Type, 完全不采纳前端给的那两个。
        //    顺带修了老代码的 bug: 原来从文件名取扩展名, 文件名没有 "." 时 substring(-1) 会崩。
        String objectName = bizType + "/" + LocalDate.now() + "/"
                + UUID.randomUUID() + "." + type.ext;

        // ⑦ 上传到 MinIO。contentType 用我们检测出的可信值, 而不是 file.getContentType()。
        //    流用 ByteArrayInputStream 包内存里的 bytes(第 ④ 步已读好), size 用 bytes.length。
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectName)
                            .stream(is, bytes.length, -1)
                            .contentType(type.contentType)   // ← 可信的 Content-Type
                            .build()
            );
        } catch (Exception e) {
            throw new BusinessException(500, "上传失败: " + e.getMessage());
        }

        // ⑧ 返回完整访问 URL
        return props.getPublicUrl() + "/" + objectName;
    }
}
