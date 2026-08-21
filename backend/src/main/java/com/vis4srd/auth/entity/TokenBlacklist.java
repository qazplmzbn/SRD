package com.vis4srd.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("token_blacklist")
public class TokenBlacklist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tokenJti;
    private LocalDateTime expiresAt;
}
