package com.vis4srd.map.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.map.service.AmapLocationService;
import com.vis4srd.map.service.MapService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 心理援助地图 API，对齐 Python {@code map_routes.py}。
 * 机构列表返回顶层 {@code success + data + pagination}（与前端 request 解包约定一致）。
 */
@RestController
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;
    private final AmapLocationService amapLocationService;

    // ==================== 机构 ====================

    @GetMapping("/api/institutions")
    public Map<String, Object> getInstitutions(
            @RequestParam(required = false) String city,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        MapService.InstitutionPage result = mapService.getInstitutions(city, type, district, page, limit);
        long total = result.total();
        int safeLimit = Math.max(limit, 1);
        long totalPages = safeLimit == 0 ? 0 : (total + safeLimit - 1) / safeLimit;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", result.data());
        body.put("pagination", Map.of(
                "page", Math.max(page, 1),
                "limit", safeLimit,
                "total", total,
                "totalPages", totalPages));
        return body;
    }

    @GetMapping("/api/institutions/nearby")
    public Map<String, Object> getNearbyInstitutions(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(name = "radius_km", defaultValue = "10") double radiusKm,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(defaultValue = "20") int limit) {
        Double latVal = lat != null ? lat : latitude;
        Double lngVal = lng != null ? lng : longitude;
        if (latVal == null || lngVal == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "缺少坐标参数：请提供 lat+lng 或 latitude+longitude");
        }
        List<Map<String, Object>> data = mapService.getNearbyInstitutions(
                lngVal, latVal, radiusKm, limit, type);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        body.put("pagination", Map.of(
                "page", 1,
                "limit", limit,
                "total", data.size(),
                "totalPages", 1));
        return body;
    }

    @GetMapping("/api/institutions/statistics")
    public ApiResponse<Map<String, Object>> getInstitutionStatistics() {
        return ApiResponse.ok(mapService.getInstitutionStatistics());
    }

    @GetMapping("/api/institutions/poi/{poiId}")
    public ApiResponse<Map<String, Object>> getInstitutionByPoi(@PathVariable String poiId) {
        Map<String, Object> inst = mapService.getInstitutionByPoiId(poiId);
        if (inst == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "机构不存在");
        }
        return ApiResponse.ok(inst);
    }

    @GetMapping("/api/institutions/{institutionId}")
    public ApiResponse<Map<String, Object>> getInstitution(@PathVariable int institutionId) {
        Map<String, Object> inst = mapService.getInstitutionById(institutionId);
        if (inst == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "机构不存在");
        }
        return ApiResponse.ok(inst);
    }

    @GetMapping("/api/cities")
    public ApiResponse<List<Map<String, Object>>> getCities() {
        return ApiResponse.ok(mapService.getCities());
    }

    @GetMapping("/api/cities/coordinates")
    public ApiResponse<Map<String, Object>> getCityCoordinates() {
        return ApiResponse.ok(mapService.getCityCoordinates());
    }

    @GetMapping("/api/districts")
    public ApiResponse<List<String>> getDistricts(@RequestParam String city) {
        return ApiResponse.ok(mapService.getDistricts(city));
    }

    @GetMapping("/api/institution-types")
    public ApiResponse<List<String>> getInstitutionTypes() {
        return ApiResponse.ok(mapService.getInstitutionTypes());
    }

    // ==================== 热线 ====================

    @GetMapping("/api/hotlines")
    public ApiResponse<List<Map<String, Object>>> getHotlines(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String province,
            @RequestParam(name = "hotline_type", required = false) String hotlineType) {
        return ApiResponse.ok(mapService.getHotlines(region, city, province, hotlineType));
    }

    @GetMapping("/api/hotlines/national")
    public ApiResponse<List<Map<String, Object>>> getNationalHotlines() {
        return ApiResponse.ok(mapService.getNationalHotlines());
    }

    @GetMapping("/api/hotlines/local")
    public ApiResponse<List<Map<String, Object>>> getLocalHotlines(@RequestParam String city) {
        return ApiResponse.ok(mapService.getLocalHotlines(city));
    }

    @GetMapping("/api/hotlines/statistics")
    public ApiResponse<Map<String, Object>> getHotlineStatistics() {
        return ApiResponse.ok(mapService.getHotlineStatistics());
    }

    @GetMapping("/api/hotlines/number/{hotline}")
    public ApiResponse<Map<String, Object>> getHotlineByNumber(@PathVariable String hotline) {
        Map<String, Object> data = mapService.getHotlineByNumber(hotline);
        if (data == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "热线不存在");
        }
        return ApiResponse.ok(data);
    }

    @GetMapping("/api/hotlines/{hotlineId}")
    public ApiResponse<Map<String, Object>> getHotline(@PathVariable int hotlineId) {
        Map<String, Object> data = mapService.getHotlineById(hotlineId);
        if (data == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "热线不存在");
        }
        return ApiResponse.ok(data);
    }

    @GetMapping("/api/regions")
    public ApiResponse<List<Map<String, Object>>> getRegions() {
        return ApiResponse.ok(mapService.getRegions());
    }

    // ==================== 定位 / 逆地理 ====================

    @GetMapping("/api/ip-location")
    public ApiResponse<Map<String, Object>> getIpLocation(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String acceptLanguage = request.getHeader("Accept-Language");
        return ApiResponse.ok(amapLocationService.resolveIpLocation(clientIp, acceptLanguage));
    }

    @GetMapping("/api/geocode/reverse")
    public ApiResponse<Map<String, Object>> reverseGeocode(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ApiResponse.ok(amapLocationService.reverseGeocode(lat, lng));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String clientIp;
        if (xff != null && !xff.isBlank()) {
            clientIp = xff.split(",")[0].trim();
        } else {
            clientIp = request.getRemoteAddr() == null ? "127.0.0.1" : request.getRemoteAddr();
        }
        if (isPrivateIp(clientIp)) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                clientIp = realIp.split(",")[0].trim();
            }
        }
        return clientIp;
    }

    private static boolean isPrivateIp(String ip) {
        if (ip == null) {
            return true;
        }
        return ip.startsWith("10.")
                || ip.startsWith("172.")
                || ip.startsWith("192.")
                || ip.startsWith("127.")
                || "::1".equals(ip)
                || "0.0.0.0".equals(ip);
    }
}
