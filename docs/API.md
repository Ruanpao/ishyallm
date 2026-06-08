# IshyaLLM API 文档

Base URL: `http://localhost:8080`

认证方式：所有 API（除登录/注册）需要在请求头中携带 `Authorization: Bearer <token>`

---

## 1. 认证

### 1.1 医生注册

```
POST /api/auth/register
Content-Type: application/json
```

**请求体：**
```json
{
    "username": "zhangsan",
    "password": "123456",
    "name": "张三",
    "department": "心内科",
    "role": "DOCTOR"
}
```

**响应 200：**
```json
{
    "id": 1,
    "username": "zhangsan",
    "name": "张三",
    "department": "心内科",
    "role": "DOCTOR",
    "enabled": true
}
```

> 注意：密码字段不会被返回。

---

### 1.2 医生登录

```
POST /api/auth/login
Content-Type: application/json
```

**请求体：**
```json
{
    "username": "zhangsan",
    "password": "123456"
}
```

**响应 200：**
```json
{
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
        "id": 1,
        "username": "zhangsan",
        "name": "张三",
        "department": "心内科",
        "role": "DOCTOR",
        "enabled": true
    }
}
```

**响应 401：**
```json
{
    "status": 401,
    "message": "Invalid password"
}
```

> 登录成功后返回的 `token` 需要在后续请求的 `Authorization` 头中使用。

---

## 2. 流式问答

### 2.1 SSE 流式提问

```
POST /api/chat/stream
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
    "query": "高血压的定义是什么",
    "history": null,
    "department": "心内科"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 是 | 用户问题 |
| history | string | 否 | 历史对话上下文（格式见下） |
| department | string | 是 | 当前科室 |

**history 格式：**
```
用户：什么是高血压？
助手：高血压是指动脉血压持续升高的状态。
用户：它的诊断标准是什么？
```

**响应：** `text/event-stream`（SSE 流式）

响应以 `[flash]` 或 `[pro]` 开头，标识本次使用的模型：

```
data:[flash] 高血压是指动脉血压持续升高的一种慢性疾病...

data:[pro] 高血压的鉴别诊断需要排除继发性高血压...
```

**响应头：**
```
Content-Type: text/event-stream
Connection: keep-alive
Cache-Control: no-cache
```

> 短问题（≤10字）自动走 Flash 模型（省钱快速），长问题（>20字）走 Pro 模型（精准推理）。

---

## 3. 对话历史

### 3.1 获取历史对话列表

```
GET /api/chat/history
Authorization: Bearer <token>
X-Doctor-Id: 1
```

**响应 200：**
```json
[
    {
        "id": 1,
        "doctorId": "1",
        "title": "高血压咨询",
        "messages": [
            {"role": "user", "content": "高血压的定义是什么", "sources": null},
            {"role": "assistant", "content": "高血压是指动脉血压持续升高...", "sources": "[1][2]"}
        ],
        "createdAt": "2026-06-08T12:00:00",
        "updatedAt": "2026-06-08T12:05:00"
    }
]
```

### 3.2 获取单条对话详情

```
GET /api/chat/history/{id}
Authorization: Bearer <token>
X-Doctor-Id: 1
```

**响应 200：** 同上的单条对话对象。

**响应 404：** 对话不存在。

---

## 4. 文档管理

### 4.1 上传 PDF

```
POST /api/documents/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | PDF 文件 |
| department | string | 是 | 文档所属科室 |

**响应 200：**
```json
{
    "id": 1,
    "title": "test.pdf",
    "department": "心内科",
    "uploadedBy": "unknown",
    "status": "UPLOADED",
    "createdAt": "2026-06-08T12:00:00"
}
```

**响应 409：** 相同文件名的文档已存在：
```
Document already exists: test.pdf
```

**响应 400：**
```
Failed to parse PDF: ...
```

> 上传后会自动触发 Kafka 管线：PDF 解析 → 分块 → 千问 Embedding → 写入 PGVector。

### 4.2 文档列表

```
GET /api/documents
Authorization: Bearer <token>
```

可选参数：`?department=心内科` 按科室筛选。

**响应 200：**
```json
[
    {
        "id": 1,
        "title": "test.pdf",
        "department": "心内科",
        "status": "UPLOADED",
        "createdAt": "2026-06-08T12:00:00"
    }
]
```

### 4.3 文档解析状态

```
GET /api/documents/{id}/status
Authorization: Bearer <token>
```

**响应 200：**
```json
{
    "id": 1,
    "title": "test.pdf",
    "status": "UPLOADED",
    ...
}
```

**响应 404：** 文档不存在。

---

## 5. 管理 API

### 5.1 创建医生账号（管理员）

```
POST /api/admin/users
Authorization: Bearer <token>  （需 ADMIN 角色）
Content-Type: application/json
```

**请求体：**
```json
{
    "username": "newdoctor",
    "password": "123456",
    "name": "新医生",
    "department": "儿科",
    "role": "DOCTOR"
}
```

**响应 200：** 同注册响应。

**响应 403：** 非管理员调用。
```
Admin only
```

### 5.2 用户列表（管理员）

```
GET /api/admin/users
Authorization: Bearer <token>  （需 ADMIN 角色）
```

**响应 200：**
```json
{
    "message": "User list - implement via UserRepository"
}
```

> 当前为占位响应。

### 5.3 系统状态

```
GET /api/admin/stats
Authorization: Bearer <token>  （需 ADMIN 角色）
```

**响应 200：**
```json
{
    "database": "connected",
    "status": "running"
}
```

---

## 6. 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证（token 缺失或无效） |
| 403 | 无权访问（非管理员） |
| 404 | 资源不存在 |
| 409 | 资源冲突（重复上传） |
| 500 | 服务器内部错误 |

---

## 7. 完整调用示例

```powershell
# 1. 注册
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"doc\",\"password\":\"123\",\"name\":\"医生\",\"department\":\"心内科\",\"role\":\"DOCTOR\"}"

# 2. 登录拿 token
$token = (curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"doc\",\"password\":\"123\"}" | ConvertFrom-Json).token

# 3. 上传 PDF
curl -s -X POST http://localhost:8080/api/documents/upload -H "Authorization: Bearer $token" -F "file=@test.pdf" -F "department=心内科"

# 4. 流式问答
curl -N -X POST http://localhost:8080/api/chat/stream -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "{\"query\":\"高血压的定义是什么\",\"history\":null,\"department\":\"心内科\"}"

# 5. 对话历史
curl -s http://localhost:8080/api/chat/history -H "Authorization: Bearer $token" -H "X-Doctor-Id: 1"

# 6. 管理员查看状态
curl -s http://localhost:8080/api/admin/stats -H "Authorization: Bearer <admin-token>"
```
