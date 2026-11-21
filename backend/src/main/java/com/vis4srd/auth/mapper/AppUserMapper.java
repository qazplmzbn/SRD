package com.vis4srd.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vis4srd.auth.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
    // Authorization must not reuse session or second-level cached roles after review/revocation.
    @Select("SELECT id, username, nickname, email, avatar_color, role, is_active, last_login_at, created_at FROM app_users WHERE id=#{id}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    AppUser selectCurrentIdentity(@Param("id") Long id);
}
