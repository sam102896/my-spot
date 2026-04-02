# 接口文档（精简版）

统一说明
- 后端地址：`http://localhost:3001`
- 认证方式：`Authorization: Bearer <token>`
- 设备标识：`X-Device-Id: <任意字符串>`（前端自动生成并携带）
- 幂等下单：`X-Idempotency-Key: <uuid>`（前端每次下单自动生成）
- 金额单位：全部使用 atomic（见 [data.md](./data.md)）

## 公开接口（无需登录）

### 注册
- POST `/api/public/auth/register`

请求
```json
{ "email": "u@example.com", "phone": null, "password": "Passw0rd!" }
```

响应
```json
{ "userId": "..." }
```

### 获取登录验证码（两步登录的第一步）
- POST `/api/public/auth/login/otp`

请求
```json
{ "identifier": "alice@example.com", "password": "Passw0rd!" }
```

响应（开发模式会直接回显 OTP）
```json
{ "otp": "123456" }
```

### 登录（两步登录的第二步）
- POST `/api/public/auth/login`

请求
```json
{ "identifier": "alice@example.com", "otp": "123456" }
```

响应
```json
{ "token": "..." }
```

### 找回/重置密码
- POST `/api/public/auth/password/reset/otp`
- POST `/api/public/auth/password/reset`

### 行情与市场数据
- GET `/api/public/market/pairs`
- GET `/api/public/market/orderbook?pair=BTCUSDT`
- GET `/api/public/market/trades?pair=BTCUSDT&limit=20`
- GET `/api/public/market/kline?pair=BTCUSDT&limit=30`
- WebSocket：`ws://localhost:3001/api/public/ws/market?pair=BTCUSDT`

### 开发环境：模拟充值（需要 AdminKey）
- POST `/api/public/admin/deposits/simulate`
- Header：`X-Admin-Key: dev-admin-key`

请求
```json
{ "identifier": "alice@example.com", "asset": "USDT", "amount": "100" }
```

响应
```json
{ "id": "...", "txId": "...", "status": "PENDING" }
```

说明：后台会在约 5 秒后自动确认入账（模拟链上到账监测）。

## 登录后接口

### 账户信息
- GET `/api/account/me`

### 基础 KYC
- POST `/api/account/kyc`

请求
```json
{ "name": "Alice" }
```

### 资产与流水
- GET `/api/account/wallets`
- GET `/api/account/ledger?asset=USDT&limit=50`

### 充值地址与充值记录
- GET `/api/account/deposit/address?asset=USDT`
- GET `/api/account/deposits?limit=50`

### 提现（需资金密码 + KYC）
- POST `/api/account/withdraw`

请求
```json
{ "asset": "USDT", "address": "ADDR-USDT-EXTERNAL", "amount": "10", "fundPassword": "123456" }
```

说明：提现会先冻结（金额+手续费），后台会自动推进状态并在约 10 秒内完成。

- POST `/api/account/withdraw/{id}/cancel`
- GET `/api/account/withdrawals?limit=50`

### 密码与设备
- POST `/api/account/password/login`
- POST `/api/account/password/fund`
- POST `/api/account/devices/bind`
- GET `/api/account/logs?limit=50`

## 现货交易

### 下单（限价/市价）
- POST `/api/trade/order`

Header
- `X-Idempotency-Key: <uuid>`

请求（限价）
```json
{ "pair": "BTCUSDT", "side": "BUY", "type": "LIMIT", "price": "65000", "qty": "0.001" }
```

请求（市价）
```json
{ "pair": "BTCUSDT", "side": "SELL", "type": "MARKET", "qty": "0.001" }
```

说明
- 限价/市价都遵循：价格优先、时间优先。
- 市价单为 IOC：撮合后未成交部分会自动撤销/拒绝，不会挂在订单簿里。
- 下单前会校验余额、最小数量/金额、KYC 状态。

### 撤单
- POST `/api/trade/order/{id}/cancel`

### 订单查询
- GET `/api/trade/open-orders?limit=50`
- GET `/api/trade/orders?limit=100`

