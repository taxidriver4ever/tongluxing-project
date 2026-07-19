package com.tongluxing.auth.service;

import com.tongluxing.auth.dto.AdminLoginRequest;
import com.tongluxing.auth.vo.AdminCurrentOperatorResponse;
import com.tongluxing.auth.vo.AdminLoginResponse;
import com.tongluxing.auth.vo.AdminLogoutResponse;

/** Web Admin 独立认证用例。 */
public interface AdminAuthService {
    AdminLoginResponse login(AdminLoginRequest request);
    AdminCurrentOperatorResponse current(String authorization);
    AdminLogoutResponse logout(String authorization);
}
