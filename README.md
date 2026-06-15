# 群体网站

当前可运行代码只保留在以下目录：

- `frontend/`：前端首页、后台管理页、样式和本地 Vue / Element Plus 静态依赖。
- `backend/`：Spring Boot 接口、Excel 导入、照片接口、资金关系图谱。
- `tests/`：迁移和页面结构相关测试。
- `docs/MIGRATION_CHECKLIST.md`：迁移清单和启动说明。
- `deploy/centos7/`：CentOS 7 部署配置，包含 systemd、Nginx 和环境变量模板。

不要从项目根目录启动静态服务。前端必须从 `frontend/` 目录启动：

```bash
cd /Users/geckomoria/Documents/群体网站/frontend
python3 -m http.server 5174
```

后端从 `backend/` 目录启动：

```bash
cd /Users/geckomoria/Documents/群体网站/backend
mvn spring-boot:run
```

后台管理入口：

```text
http://localhost:5174/admin.html
```

CentOS 7 部署请看：

```text
deploy/centos7/README.md
```
