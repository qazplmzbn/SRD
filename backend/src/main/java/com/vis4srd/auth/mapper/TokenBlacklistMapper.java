package com.vis4srd.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vis4srd.auth.entity.TokenBlacklist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {
}
