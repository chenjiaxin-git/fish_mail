package com.fishmall.catalog.api.controller;

import com.fishmall.catalog.api.vo.FileUploadResponse;
import com.fishmall.catalog.application.service.AdminFileService;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/files")
public class AdminFileController {
    private final AdminFileService fileService;

    public AdminFileController(AdminFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public ApiResponse<FileUploadResponse> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(fileService.upload(file), traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
