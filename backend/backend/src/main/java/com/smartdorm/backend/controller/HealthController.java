package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("project", "SmartDorm Backend");
        data.put("status", "UP");
        data.put("time", LocalDateTime.now());
        return Result.success(data);
    }
}
