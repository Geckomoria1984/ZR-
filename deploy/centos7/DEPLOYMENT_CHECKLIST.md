# CentOS 7 部署检查清单

部署前确认：

- 服务器安装了 `Java 17`。
- 服务器安装了 `Nginx`。
- 后端 Jar 已生成：`backend/target/group-dashboard-0.0.1-SNAPSHOT.jar`。
- H2 数据库文件已准备：`backend/data/group-dashboard.mv.db`。
- 前端目录已准备：`frontend/`。
- 照片目录已准备，如果没有照片可以先空着。

服务器目录确认：

- `/opt/group-dashboard/backend/group-dashboard.jar`
- `/opt/group-dashboard/backend/data/group-dashboard.mv.db`
- `/opt/group-dashboard/frontend/index.html`
- `/opt/group-dashboard/frontend/admin.html`
- `/opt/group-dashboard/frontend/src/`
- `/etc/group-dashboard/group-dashboard.env`
- `/etc/systemd/system/group-dashboard.service`
- `/etc/nginx/conf.d/group-dashboard.conf`

服务确认：

- `systemctl status group-dashboard` 是 running。
- `systemctl status nginx` 是 running。
- `curl http://127.0.0.1:8080/api/dashboard` 有返回。
- `curl http://服务器IP/api/dashboard` 有返回。

浏览器确认：

- `http://服务器IP/index.html` 能打开首页。
- `http://服务器IP/admin.html` 能打开后台。
- 首页统计数据能显示。
- 个人信息页面能打开。
- 图片能显示。
- 后台导入功能能调用接口。

铁律：

- 运行数据从 H2 数据库读取。
- Excel 只用于后台导入到数据库。
- 迁移必须带走 `group-dashboard.mv.db`。
- 不要在 CentOS 7 上默认切到 MySQL，除非已经做过完整数据迁移。
