package com.vis4srd.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vis4srd.auth.dto.UserView;
import com.vis4srd.auth.entity.AppUser;
import com.vis4srd.auth.entity.TokenBlacklist;
import com.vis4srd.auth.mapper.AppUserMapper;
import com.vis4srd.auth.mapper.TokenBlacklistMapper;
import com.vis4srd.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String[] AVATAR_COLORS = {
            "#C19A83", "#A07D6B", "#8B6F5E",
            "#7B9E89", "#5A7A63",
            "#8BA5B5", "#6B8A9E",
            "#B5A07B", "#9E8A5A",
            "#A08B8B", "#8E7474",
            "#8B8BA5", "#74749E"
    };

    private final AppUserMapper appUserMapper;
    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUser register(String username, String password, String nickname) {
        Long exists = appUserMapper.selectCount(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在，请换一个");
        }

        String finalNickname = StringUtils.hasText(nickname) ? nickname.trim() : username;
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setNickname(finalNickname);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAvatarColor(AVATAR_COLORS[ThreadLocalRandom.current().nextInt(AVATAR_COLORS.length)]);
        user.setRole("user");
        user.setIsActive(true);
        appUserMapper.insert(user);
        return user;
    }

    @Transactional
    public AppUser authenticate(String username, String password) {
        AppUser user = appUserMapper.selectOne(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUsername, username));
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        appUserMapper.updateById(user);
        return user;
    }

    public UserView getUserById(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return toView(user, true);
    }

    public UserView toView(AppUser user, boolean detailed) {
        UserView.UserViewBuilder builder = UserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .avatarColor(user.getAvatarColor())
                .role(StringUtils.hasText(user.getRole()) ? user.getRole() : "user");
        if (detailed) {
            builder.email(user.getEmail())
                    .isActive(user.getIsActive())
                    .lastLoginAt(user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString())
                    .createdAt(user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        }
        return builder.build();
    }

    @Transactional
    public void revokeToken(String jti, Long expEpochSeconds) {
        TokenBlacklist row = new TokenBlacklist();
        row.setTokenJti(jti);
        Instant exp = expEpochSeconds == null ? Instant.now() : Instant.ofEpochSecond(expEpochSeconds);
        row.setExpiresAt(LocalDateTime.ofInstant(exp, ZoneOffset.UTC));
        tokenBlacklistMapper.insert(row);
    }

    public boolean isTokenRevoked(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        Long count = tokenBlacklistMapper.selectCount(
                Wrappers.<TokenBlacklist>lambdaQuery().eq(TokenBlacklist::getTokenJti, jti));
        return count != null && count > 0;
    }

    @Transactional
    public String changePassword(Long userId, String oldPassword, String newPassword) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserMapper.updateById(user);
        return "密码修改成功";
    }

    @Transactional
    public void updateNickname(Long userId, String nickname) {
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException("昵称不能为空");
        }
        AppUser user = new AppUser();
        user.setId(userId);
        user.setNickname(nickname.trim());
        appUserMapper.updateById(user);
    }
}
