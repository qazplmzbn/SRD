package com.vis4srd.map.service;

import com.vis4srd.map.config.AmapProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 高德 IP 定位与逆地理编码；无 key 或失败时降级，不抛 500。
 */
@Slf4j
@Service
public class AmapLocationService {

    private static final Map<String, double[]> CITY_COORDS = Map.ofEntries(
            Map.entry("北京", new double[]{116.4074, 39.9042}),
            Map.entry("北京市", new double[]{116.4074, 39.9042}),
            Map.entry("上海", new double[]{121.4737, 31.2304}),
            Map.entry("上海市", new double[]{121.4737, 31.2304}),
            Map.entry("广州", new double[]{113.2644, 23.1291}),
            Map.entry("广州市", new double[]{113.2644, 23.1291}),
            Map.entry("深圳", new double[]{114.3055, 22.5431}),
            Map.entry("深圳市", new double[]{114.3055, 22.5431}),
            Map.entry("成都", new double[]{104.0665, 30.5728}),
            Map.entry("成都市", new double[]{104.0665, 30.5728}),
            Map.entry("武汉", new double[]{114.3055, 30.5928}),
            Map.entry("武汉市", new double[]{114.3055, 30.5928}),
            Map.entry("西安", new double[]{108.9543, 34.3416}),
            Map.entry("西安市", new double[]{108.9543, 34.3416}),
            Map.entry("重庆", new double[]{106.5516, 29.5630}),
            Map.entry("重庆市", new double[]{106.5516, 29.5630}),
            Map.entry("天津", new double[]{117.2008, 39.1256}),
            Map.entry("天津市", new double[]{117.2008, 39.1256}),
            Map.entry("杭州", new double[]{120.1536, 30.2744}),
            Map.entry("杭州市", new double[]{120.1536, 30.2744}),
            Map.entry("南京", new double[]{118.7969, 32.0603}),
            Map.entry("南京市", new double[]{118.7969, 32.0603}),
            Map.entry("长沙", new double[]{112.9388, 28.2282}),
            Map.entry("长沙市", new double[]{112.9388, 28.2282}),
            Map.entry("郑州", new double[]{113.6484, 34.7566}),
            Map.entry("郑州市", new double[]{113.6484, 34.7566}));

    private final AmapProperties amapProperties;
    private final RestTemplate restTemplate;

    public AmapLocationService(AmapProperties amapProperties, RestTemplateBuilder builder) {
        this.amapProperties = amapProperties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> resolveIpLocation(String clientIp, String acceptLanguage) {
        String key = amapProperties.getKey();
        if (key != null && !key.isBlank()) {
            Map<String, Object> amap = tryAmapIp(clientIp, key);
            if (amap != null) {
                return amap;
            }
        }
        Map<String, Object> ipApi = tryIpApi(clientIp);
        if (ipApi != null) {
            return ipApi;
        }
        return defaultLocation(
                acceptLanguage != null && acceptLanguage.toLowerCase(Locale.ROOT).contains("zh")
                        ? "default-cn"
                        : "default");
    }

    public Map<String, Object> reverseGeocode(double lat, double lng) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("formattedAddress", "");
        fallback.put("shortAddress", "");
        fallback.put("province", "");
        fallback.put("city", "");
        fallback.put("district", "");
        fallback.put("township", "");
        fallback.put("street", "");
        fallback.put("streetNumber", "");
        fallback.put("location", Map.of("lat", lat, "lng", lng));
        fallback.put("nearestPoi", null);
        fallback.put("source", "fallback");

        if (lat < 15 || lat > 60 || lng < 70 || lng > 140) {
            fallback.put("formattedAddress", "坐标超出中国范围");
            fallback.put("source", "out-of-range");
            return fallback;
        }

        String key = amapProperties.getKey();
        if (key == null || key.isBlank()) {
            fallback.put("source", "no-amap-key");
            return fallback;
        }

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://restapi.amap.com/v3/geocode/regeo")
                    .queryParam("key", key)
                    .queryParam("location", lng + "," + lat)
                    .queryParam("extensions", "all")
                    .queryParam("radius", 1000)
                    .queryParam("output", "JSON")
                    .toUriString();
            Map<String, Object> data = getJson(url);
            if (data == null || !"1".equals(String.valueOf(data.get("status")))) {
                return fallback;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> regeocode = (Map<String, Object>) data.get("regeocode");
            if (regeocode == null) {
                return fallback;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> addressComponent = (Map<String, Object>) regeocode.getOrDefault(
                    "addressComponent", Map.of());
            String province = asString(addressComponent.get("province"));
            String city = asString(addressComponent.get("city"));
            if (city.isBlank()) {
                city = province;
            }
            String district = asString(addressComponent.get("district"));
            String township = asString(addressComponent.get("township"));
            @SuppressWarnings("unchecked")
            Map<String, Object> streetNumber = (Map<String, Object>) addressComponent.getOrDefault(
                    "streetNumber", Map.of());
            String street = asString(streetNumber.get("street"));
            String number = asString(streetNumber.get("number"));

            StringBuilder shortAddr = new StringBuilder();
            shortAddr.append(district).append(township).append(street).append(number);
            if (shortAddr.isEmpty()) {
                shortAddr.append(city);
            }

            Map<String, Object> nearestPoi = null;
            Object poisObj = regeocode.get("pois");
            if (poisObj instanceof List<?> pois && !pois.isEmpty()) {
                Object first = pois.get(0);
                if (first instanceof Map<?, ?> poi) {
                    nearestPoi = new LinkedHashMap<>();
                    nearestPoi.put("name", asString(poi.get("name")));
                    nearestPoi.put("address", asString(poi.get("address")));
                    nearestPoi.put("distance", asString(poi.get("distance")));
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("formattedAddress", asString(regeocode.get("formatted_address")));
            result.put("shortAddress", shortAddr.toString());
            result.put("province", province);
            result.put("city", city);
            result.put("district", district);
            result.put("township", township);
            result.put("street", street);
            result.put("streetNumber", number);
            result.put("location", Map.of("lat", lat, "lng", lng));
            result.put("nearestPoi", nearestPoi);
            result.put("source", "amap");
            return result;
        } catch (Exception ex) {
            log.warn("逆地理编码失败: {}", ex.getMessage());
            return fallback;
        }
    }

    private Map<String, Object> tryAmapIp(String clientIp, String key) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://restapi.amap.com/v3/ip")
                    .queryParam("key", key)
                    .queryParam("ip", clientIp == null ? "" : clientIp)
                    .toUriString();
            Map<String, Object> data = getJson(url);
            if (data == null || !"1".equals(String.valueOf(data.get("status")))) {
                return null;
            }
            String province = asString(data.get("province"));
            if (province.isBlank()) {
                return null;
            }
            String city = asString(data.get("city"));
            if (city.isBlank()) {
                city = province;
            }
            double[] coords = cityCenter(city, key);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("city", city);
            result.put("province", province);
            result.put("adcode", asString(data.get("adcode")));
            result.put("longitude", coords[0]);
            result.put("latitude", coords[1]);
            result.put("formattedAddress", province + city);
            result.put("source", "amap");
            return result;
        } catch (Exception ex) {
            log.warn("高德 IP 定位失败: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> tryIpApi(String clientIp) {
        try {
            String ip = clientIp == null || clientIp.isBlank() ? "" : clientIp;
            String url = "http://ip-api.com/json/" + ip
                    + "?fields=status,country,regionName,city,lat,lon,query";
            Map<String, Object> data = getJson(url);
            if (data == null || !"success".equals(String.valueOf(data.get("status")))) {
                return null;
            }
            String city = asString(data.get("city"));
            String province = asString(data.get("regionName"));
            Map<String, String> mapped = mapChineseCity(city, province);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("city", mapped.get("city"));
            result.put("province", mapped.get("province"));
            result.put("adcode", "");
            result.put("longitude", toDouble(data.get("lon"), 106.5516));
            result.put("latitude", toDouble(data.get("lat"), 29.5630));
            result.put("formattedAddress", province + city);
            result.put("source", "ip-api");
            return result;
        } catch (Exception ex) {
            log.warn("ip-api 定位失败: {}", ex.getMessage());
            return null;
        }
    }

    private double[] cityCenter(String cityName, String key) {
        if (cityName != null && CITY_COORDS.containsKey(cityName)) {
            return CITY_COORDS.get(cityName);
        }
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://restapi.amap.com/v3/geocode/geo")
                    .queryParam("key", key)
                    .queryParam("address", cityName)
                    .queryParam("city", cityName)
                    .toUriString();
            Map<String, Object> data = getJson(url);
            if (data != null && "1".equals(String.valueOf(data.get("status")))) {
                Object geocodes = data.get("geocodes");
                if (geocodes instanceof List<?> list && !list.isEmpty()
                        && list.get(0) instanceof Map<?, ?> geo) {
                    String location = asString(geo.get("location"));
                    String[] parts = location.split(",");
                    if (parts.length == 2) {
                        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return new double[]{116.4074, 39.9042};
    }

    private Map<String, Object> defaultLocation(String source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", "重庆市");
        result.put("province", "重庆市");
        result.put("adcode", "500000");
        result.put("longitude", 106.5516);
        result.put("latitude", 29.5630);
        result.put("formattedAddress", "重庆市");
        result.put("source", source);
        return result;
    }

    private Map<String, String> mapChineseCity(String englishCity, String englishRegion) {
        Map<String, String[]> mapping = new LinkedHashMap<>();
        mapping.put("beijing", new String[]{"北京市", "北京"});
        mapping.put("shanghai", new String[]{"上海市", "上海"});
        mapping.put("tianjin", new String[]{"天津市", "天津"});
        mapping.put("chongqing", new String[]{"重庆市", "重庆"});
        mapping.put("guangzhou", new String[]{"广州市", "广东省"});
        mapping.put("shenzhen", new String[]{"深圳市", "广东省"});
        mapping.put("chengdu", new String[]{"成都市", "四川省"});
        mapping.put("hangzhou", new String[]{"杭州市", "浙江省"});
        mapping.put("nanjing", new String[]{"南京市", "江苏省"});
        mapping.put("wuhan", new String[]{"武汉市", "湖北省"});
        mapping.put("xian", new String[]{"西安市", "陕西省"});
        mapping.put("changsha", new String[]{"长沙市", "湖南省"});
        mapping.put("zhengzhou", new String[]{"郑州市", "河南省"});

        String cityLower = englishCity == null ? "" : englishCity.toLowerCase(Locale.ROOT).trim();
        String regionLower = englishRegion == null ? "" : englishRegion.toLowerCase(Locale.ROOT).trim();
        if (mapping.containsKey(cityLower)) {
            String[] v = mapping.get(cityLower);
            return Map.of("city", v[0], "province", v[1]);
        }
        if (mapping.containsKey(regionLower)) {
            String[] v = mapping.get(regionLower);
            return Map.of("city", v[0], "province", v[1]);
        }
        return Map.of(
                "city", (englishCity == null || englishCity.isBlank()) ? "重庆市" : englishCity,
                "province", (englishRegion == null || englishRegion.isBlank()) ? "重庆市" : englishRegion);
    }

    private Map<String, Object> getJson(String url) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        return response.getBody();
    }

    private static String asString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "" : String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
