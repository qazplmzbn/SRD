/**
 * 心理援助地图业务域，对齐 Python {@code backend/src/routes/map_routes.py} 与
 * {@code backend/src/services/map_service.py}。
 *
 * <p>主要能力：机构列表/附近检索（PostGIS）、热线、城市坐标、高德 IP/逆地理编码。
 * 包结构：{@code controller} / {@code service} / {@code config}。
 * PostgreSQL 不可用时查询降级为空列表；高德无 key 或失败时定位接口降级，不返回 500。
 */
package com.vis4srd.map;
