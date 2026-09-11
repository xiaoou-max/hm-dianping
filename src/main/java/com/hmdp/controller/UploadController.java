package com.hmdp.controller;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

// 图片上传控制器（/upload）：笔记图片上传、删除
// 教学简化：直接存本地磁盘，生产建议改对象存储（OSS/S3）
@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    // 上传图片：接收文件，重命名后存到本地目录
    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 取原始文件名
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名（UUID+后缀，避免重名）
            String fileName = createNewFileName(originalFilename);
            // 把文件保存到本地上传目录
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            log.debug("文件上传成功，{}", fileName);
            // 返回文件访问路径给前端
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    // 删除图片
    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        // 定位到要删的文件
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        // 防止误传目录名
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        // 删除文件
        FileUtil.del(file);
        return Result.ok();
    }

    // 生成新文件名：UUID做文件名 + 按哈希分两级目录（防止单目录文件过多）
    private String createNewFileName(String originalFilename) {
        // 取文件后缀（.png等）
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // UUID作为文件名
        String name = UUID.randomUUID().toString();
        // 用hash的前4位分两级目录（d1、d2各0~15），把文件分散开
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 目录不存在就创建
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 返回形如 /blogs/3/5/uuid.png 的路径
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
