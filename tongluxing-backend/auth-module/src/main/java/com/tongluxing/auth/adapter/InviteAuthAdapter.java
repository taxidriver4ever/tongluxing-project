package com.tongluxing.auth.adapter;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.tongluxing.auth.entity.AuthAccount;
import com.tongluxing.auth.mapper.AuthAccountMapper;
import com.tongluxing.invite.integration.InviteAuthPort;

import lombok.RequiredArgsConstructor;

/**
 * auth-module 提供给 invite-module 的认证账号适配器。
 *
 * <p>invite-module 只依赖 {@link InviteAuthPort} 端口，不直接访问 auth_account 表。
 * 本适配器负责把 invite 的“注册时间查询、手机号查询邀请人”需求转换为 auth-module 内部 Mapper 查询。</p>
 */
@Component
@RequiredArgsConstructor
public class InviteAuthAdapter implements InviteAuthPort {

    /** 认证账号 Mapper，仅在 auth-module 内部使用。 */
    private final AuthAccountMapper accountMapper;

    /**
     * 查询用户注册时间。
     *
     * @param userId 用户 ID
     * @return auth_account.created_at；账号不存在或已删除时返回空
     */
    @Override
    public Optional<LocalDateTime> findRegisteredAt(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        AuthAccount account = accountMapper.findByUserId(userId);
        return Optional.ofNullable(account).map(AuthAccount::getCreatedAt);
    }

    /**
     * 根据手机号查询可作为邀请人的用户 ID。
     *
     * <p>只有正常状态账号才能作为邀请人；禁用或不存在的账号返回空。</p>
     */
    @Override
    public Optional<Long> findAvailableUserIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        AuthAccount account = accountMapper.findByPhone(phone.trim());
        if (account == null || !Integer.valueOf(1).equals(account.getAccountStatus())) {
            return Optional.empty();
        }
        return Optional.ofNullable(account.getUserId());
    }
}
