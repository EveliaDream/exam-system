package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 题干配图存储。
 * <p>
 * 三个安全要点：
 * <ol>
 *   <li><b>只认图片扩展名 + 校验文件头魔数</b>：改扩展名上传 html/svg 会造成 XSS，
 *       魔数校验能挡住大部分伪装</li>
 *   <li><b>文件名由服务端生成 UUID</b>：不用用户提供的文件名，从根上消除路径穿越
 *       （`../../etc/passwd`）与覆盖已有文件的风险</li>
 *   <li><b>读取时只接受「UUID.扩展名」格式</b>：正则校验挡住 `..%2f` 一类的构造</li>
 * </ol>
 * 图片读取接口是匿名的（&lt;img src&gt; 带不了 token），安全性靠"文件名不可猜"而非鉴权，
 * 这对课程项目是合理取舍，见 DEV-NOTES。
 */
@Slf4j
@Service
public class FileStorageService {

    /** 允许的图片类型：扩展名 → 内容类型 */
    private static final Map<String, String> ALLOWED = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    /** 单张图片上限 2MB：题干配图不需要更大，限制能防住误传大图 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    /** 服务端生成的文件名格式，读取时用它反校验，杜绝路径穿越 */
    private static final String NAME_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|jpeg|gif|webp)$";

    private final Path root;

    public FileStorageService(@Value("${exam.upload.dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("图片存储目录: {}", root);
        } catch (IOException e) {
            log.error("无法创建图片存储目录: {}", root, e);
        }
    }

    /** 保存图片，返回可直接用于 &lt;img src&gt; 的相对地址 */
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "图片不能超过 2MB");
        }

        String ext = extensionOf(file.getOriginalFilename());
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "只支持 png / jpg / jpeg / gif / webp 格式的图片");
        }
        if (!looksLikeImage(file, ext)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "文件内容不是有效的图片，请勿修改扩展名后上传");
        }

        String filename = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            // 理论上不会发生（文件名由我们生成），保留这层校验作为纵深防御
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "非法的文件名");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("保存图片失败: {}", target, e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "图片保存失败，请稍后重试");
        }
        return new StoredImage("/api/files/" + filename, filename, contentType, file.getSize());
    }

    /** 读取图片；文件名必须是服务端生成过的格式 */
    public byte[] load(String filename) {
        if (filename == null || !filename.matches(NAME_PATTERN)) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "图片不存在");
        }
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "图片不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.error("读取图片失败: {}", target, e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "图片读取失败");
        }
    }

    public String contentTypeOf(String filename) {
        String ext = extensionOf(filename);
        return ALLOWED.getOrDefault(ext, "application/octet-stream");
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 校验文件头魔数。
     * <p>
     * 只做"是不是常见图片容器"的判断，不做像素级解析：
     * 目的是挡住"把 html/脚本改名成 .png"这类攻击，而不是防住精心构造的图片炸弹。
     */
    private boolean looksLikeImage(MultipartFile file, String ext) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return switch (ext) {
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "jpg", "jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "gif" -> head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
            case "webp" -> head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default -> false;
        };
    }

    /** 上传结果 */
    public record StoredImage(String url, String filename, String contentType, long size) {
    }
}
