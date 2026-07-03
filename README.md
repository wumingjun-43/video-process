# 牛王认证系统

基于 Spring Boot 3.x + spring-ai-alibaba + Vue 3 的牛王图片识别与匹配系统。

## 功能特性

- **牛王管理**：新增/编辑/删除牛王，支持最多4张图片上传
- **牛王匹配**：上传图片，通过 AI 自动匹配系统中的牛王数据
- **特征分析**：结合知识图谱，AI 分析牛王的共同视觉特征（牛角、牛头、牛眼、毛发等）
- **知识图谱**：上传 PDF/Word/Excel 文件，AI 自动提取内容并建立向量索引
- **用户管理**：用户的增删改查
- **匹配记录**：查看历史匹配记录和置信度

## 技术栈

### 后端
- Spring Boot 3.2.5 + JDK 21
- MyBatis-Plus 3.5.6
- MySQL 8.0
- Redis 7.x（向量存储）
- spring-ai-alibaba-starter-dashscope（图片识别 + 知识图谱分析）
- Spring Security + JWT
- Knife4j 4.4.0（API 文档）

### 前端
- Vue 3.4 + Vite 5.2
- Element Plus 2.6
- Pinia 2.1
- Axios

## 快速开始

### 1. 初始化数据库

```bash
# 创建数据库和表
mysql -u root -p123456 < backend/src/main/resources/db/init.sql
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动后访问 API 文档：http://localhost:8080/api/doc.html

### 3. 启动前端

```bash
cd admin-frontend
npm install
npm run dev
```

前端启动后访问：http://localhost:5173

### 4. 默认登录账号

- 登录名：`admin`
- 密码：`admin123`

## 项目结构

```
niuwang/
├── backend/                    # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/niuwang/
│       │   ├── NiuwangApplication.java
│       │   ├── common/         # 公共类（Result, 异常处理）
│       │   ├── config/         # 配置类
│       │   ├── controller/     # REST 控制器
│       │   ├── mapper/         # MyBatis-Plus Mapper
│       │   ├── model/          # entity, dto, vo
│       │   ├── security/       # JWT 认证
│       │   └── service/        # 业务逻辑
│       └── resources/
│           ├── application.yml
│           └── db/init.sql
└── admin-frontend/             # Vue 3 前端
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── api/               # API 请求模块
        ├── router/            # 路由配置
        ├── stores/            # Pinia 状态管理
        ├── utils/             # 工具函数
        └── views/             # 页面组件
```

## API 接口

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | POST /api/auth/login | 登录 |
| 牛王 | POST /api/bull-king | 新增牛王 |
| 牛王 | GET /api/bull-king | 分页查询 |
| 牛王 | POST /api/bull-king/match | 图片匹配 |
| 牛王 | POST /api/bull-king/features | 特征分析 |
| 用户 | POST /api/user | 新增用户 |
| 用户 | GET /api/user | 分页查询 |
| 知识 | POST /api/knowledge/upload | 上传知识文件 |
| 知识 | GET /api/knowledge | 查询知识文件 |
| 记录 | GET /api/match-record | 查询匹配记录 |
