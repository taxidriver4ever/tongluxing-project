# 同路行运营管理端

基于 Vue 3、Vue Router、Vite 与 Lucide Vue 实现的管理端项目。UI 以
`08_前端ui整理重制版/tongluxing_admin_pages` 中的 HTML 原型为唯一视觉基准。

## 启动

```bash
npm install
npm run dev
```

生产构建：

```bash
npm run build
```

## 页面

- `/login` 管理员登录
- `/` 运营总览
- `/certification-audit` 认证审核
- `/trade-management` 交易管理
- `/groupbuy-intervention` 拼团干预
- `/operation-rules` 运营规则
- `/audit-logs` 审计日志
- `/internal-interfaces` 内部接口

## 车辆认证联调

Web Admin 现在使用独立登录页和后端 Admin 会话，联调固定凭据为 `admin / Admin@123456`。同一账号 1 小时内密码错误 5 次会被后端锁定。

`/certification-audit` 已接入车辆认证真实业务链路：

- Admin 登录：`POST /v1/admin/auth/login`
- 按状态分页查询：`GET /admin/vehicle/auth/list`
- 审核通过/拒绝：`POST /admin/vehicle/auth/audit`
- 大整数 ID 会按字符串保留，避免 Snowflake ID 在浏览器中丢失精度。

认证审核页不再包含 Mock 手机号登录，未登录访问任意业务路由会自动跳转 `/login`。

本地开发时 Vite 默认将 `/api` 代理到 `http://127.0.0.1:18080`，以解决浏览器跨域问题。如使用已配置跨域的独立 API 网关，可通过 `VITE_API_BASE_URL` 覆盖请求根路径。
