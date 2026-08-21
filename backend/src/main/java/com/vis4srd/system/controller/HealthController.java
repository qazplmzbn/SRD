package com.vis4srd.system.controller;

import com.vis4srd.system.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public Map<String, Object> health() {
        return healthService.basic();
    }

    @GetMapping("/detailed")
    public Map<String, Object> detailed() {
        return healthService.detailed();
    }

    @PostMapping("/reconnect")
    public Map<String, Object> reconnect() {
        return healthService.reconnect();
    }
}
