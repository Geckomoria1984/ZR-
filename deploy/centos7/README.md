# CentOS 7 部署说明（新手版）

目标：把这个网站部署到一台 CentOS 7 服务器上，让别人通过浏览器访问：

```text
http://服务器IP/index.html
http://服务器IP/admin.html
```

部署方式：

- 前端：`frontend/` 作为静态网站，交给 Nginx 访问。
- 后端：`backend` 打包成一个 Spring Boot Jar，用 systemd 常驻运行。
- 数据库：继续使用现在的 H2 文件数据库，必须把 `backend/data/group-dashboard.mv.db` 一起迁移过去。
- 数据规则：网站运行时所有数据都从后端数据库读取，Excel 只负责导入到数据库，页面不能直接读 Excel。

## 一、部署前必须准备的东西

在本机项目里确认这些东西存在：

```text
backend/
frontend/
backend/data/group-dashboard.mv.db
deploy/centos7/
```

必须迁移到服务器的内容：

```text
后端 Jar：backend/target/group-dashboard-0.0.1-SNAPSHOT.jar
前端目录：frontend/
H2 数据库：backend/data/group-dashboard.mv.db
照片目录：如果你本机有照片目录，也要同步到服务器
部署配置：deploy/centos7/group-dashboard.service
部署配置：deploy/centos7/nginx-group-dashboard.conf
部署配置：deploy/centos7/group-dashboard.env.example
```

## 二、CentOS 7 服务器安装依赖

登录服务器后执行：

```bash
yum install -y nginx
yum install -y curl wget unzip
```

本项目后端是 Spring Boot 3，需要 Java 17。CentOS 7 自带 Java 通常太旧，必须装 Java 17。

安装好 Java 17 后检查：

```bash
java -version
```

必须看到类似：

```text
openjdk version "17..."
```

如果不是 17，后端 Jar 跑不起来。

## 三、创建服务器目录

执行：

```bash
useradd -r -s /sbin/nologin groupdash
mkdir -p /opt/group-dashboard/backend
mkdir -p /opt/group-dashboard/frontend
mkdir -p /opt/group-dashboard/backend/data
mkdir -p /etc/group-dashboard
mkdir -p /data/group-dashboard/photos
mkdir -p /data/group-dashboard/excel
chown -R groupdash:groupdash /opt/group-dashboard /data/group-dashboard
```

目录作用：

```text
/opt/group-dashboard/backend       后端 Jar 和 H2 数据库
/opt/group-dashboard/frontend      前端页面
/etc/group-dashboard               后端环境变量配置
/data/group-dashboard/photos       人员照片
/data/group-dashboard/excel        Excel 导入源文件备用目录
```

## 四、在本机打包后端

在项目根目录执行：

```bash
cd backend
mvn clean package -DskipTests
```

打包成功后会生成：

```text
backend/target/group-dashboard-0.0.1-SNAPSHOT.jar
```

## 五、把文件传到服务器

下面命令在本机执行，把 `服务器IP` 换成真实服务器地址。

上传后端 Jar：

```bash
scp backend/target/group-dashboard-0.0.1-SNAPSHOT.jar root@服务器IP:/opt/group-dashboard/backend/group-dashboard.jar
```

上传 H2 数据库文件：

```bash
scp backend/data/group-dashboard.mv.db root@服务器IP:/opt/group-dashboard/backend/data/group-dashboard.mv.db
```

上传前端目录：

```bash
rsync -av frontend/ root@服务器IP:/opt/group-dashboard/frontend/
```

如果有照片目录，也上传到服务器：

```bash
rsync -av /本机照片目录/ root@服务器IP:/data/group-dashboard/photos/
```

上传完成后，在服务器上执行一次权限修正：

```bash
chown -R groupdash:groupdash /opt/group-dashboard /data/group-dashboard
```

## 六、配置后端环境变量

在服务器上执行：

```bash
cp /你的项目路径/deploy/centos7/group-dashboard.env.example /etc/group-dashboard/group-dashboard.env
vi /etc/group-dashboard/group-dashboard.env
```

如果服务器上没有项目源码，也可以手动创建：

```bash
vi /etc/group-dashboard/group-dashboard.env
```

内容写成：

```text
SERVER_PORT=8080
DASHBOARD_EXCEL_PATH=/data/group-dashboard/excel/import-source.xlsx
DASHBOARD_PHOTO_DIR=/data/group-dashboard/photos
```

说明：

- `SERVER_PORT=8080`：后端监听 8080。
- `DASHBOARD_PHOTO_DIR`：照片目录。
- `DASHBOARD_EXCEL_PATH`：只是备用导入源路径，页面运行数据仍然从 H2 数据库读取。
- 不要写 `SPRING_PROFILES_ACTIVE=mysql`，否则会切到 MySQL，当前 H2 数据就读不到。

## 七、配置 systemd 后端服务

把服务文件复制到 systemd：

```bash
cp deploy/centos7/group-dashboard.service /etc/systemd/system/group-dashboard.service
```

如果服务器上没有源码，可以手动创建：

```bash
vi /etc/systemd/system/group-dashboard.service
```

内容：

```ini
[Unit]
Description=Group Dashboard Spring Boot API
After=network.target

[Service]
Type=simple
User=groupdash
Group=groupdash
WorkingDirectory=/opt/group-dashboard/backend
EnvironmentFile=/etc/group-dashboard/group-dashboard.env
ExecStart=/usr/bin/java -jar /opt/group-dashboard/backend/group-dashboard.jar
Restart=always
RestartSec=5
SuccessExitStatus=143
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

启动后端：

```bash
systemctl daemon-reload
systemctl enable group-dashboard
systemctl start group-dashboard
systemctl status group-dashboard
```

看后端日志：

```bash
journalctl -u group-dashboard -f
```

如果启动成功，服务器本机访问应该有返回：

```bash
curl http://127.0.0.1:8080/api/dashboard
```

## 八、配置 Nginx

把 Nginx 配置复制过去：

```bash
cp deploy/centos7/nginx-group-dashboard.conf /etc/nginx/conf.d/group-dashboard.conf
```

如果服务器上没有源码，可以手动创建：

```bash
vi /etc/nginx/conf.d/group-dashboard.conf
```

内容：

```nginx
server {
    listen 80;
    server_name _;

    root /opt/group-dashboard/frontend;
    index index.html;
    client_max_body_size 220m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_connect_timeout 60s;
        proxy_send_timeout 300s;
    }
}
```

检查 Nginx 配置：

```bash
nginx -t
```

启动 Nginx：

```bash
systemctl enable nginx
systemctl restart nginx
systemctl status nginx
```

如果服务器开启 SELinux，需要执行：

```bash
setsebool -P httpd_can_network_connect 1
```

## 九、开放防火墙

如果服务器开了防火墙，执行：

```bash
firewall-cmd --permanent --add-service=http
firewall-cmd --reload
```

如果提示没有 `firewall-cmd`，说明防火墙服务可能没开，可以先不用管。

## 十、访问网站

浏览器打开：

```text
http://服务器IP/index.html
```

后台管理：

```text
http://服务器IP/admin.html
```

前端会通过同一个域名请求：

```text
/api/dashboard
/api/admin/people
/api/admin/imports/related-people/graph
```

这些 `/api` 请求会被 Nginx 转发到后端：

```text
http://127.0.0.1:8080
```

## 十一、以后更新代码怎么发布

每次更新后端：

```bash
cd backend
mvn clean package -DskipTests
scp target/group-dashboard-0.0.1-SNAPSHOT.jar root@服务器IP:/opt/group-dashboard/backend/group-dashboard.jar
ssh root@服务器IP "chown groupdash:groupdash /opt/group-dashboard/backend/group-dashboard.jar && systemctl restart group-dashboard"
```

每次更新前端：

```bash
rsync -av frontend/ root@服务器IP:/opt/group-dashboard/frontend/
ssh root@服务器IP "systemctl reload nginx"
```

如果只是改前端页面和样式，不需要重启后端。

如果改了后端 Java 代码，必须重启后端：

```bash
systemctl restart group-dashboard
```

## 十二、常见问题

### 1. 页面能打开，但是没有数据

检查后端是否启动：

```bash
systemctl status group-dashboard
journalctl -u group-dashboard -n 100
```

检查 H2 数据库文件是否存在：

```bash
ls -lh /opt/group-dashboard/backend/data/group-dashboard.mv.db
```

如果这个文件没有迁移过去，数据就会空。

### 2. 页面提示接口失败

检查 Nginx 是否把 `/api` 转给后端：

```bash
curl http://127.0.0.1:8080/api/dashboard
curl http://服务器IP/api/dashboard
```

第一个能返回，第二个不能返回，就是 Nginx 配置问题。

### 3. 后端启动失败

看日志：

```bash
journalctl -u group-dashboard -n 200
```

重点看：

```text
java version
端口 8080 是否被占用
H2 数据库文件是否有权限
```

检查 Java：

```bash
java -version
```

检查端口：

```bash
netstat -lntp | grep 8080
```

### 4. 照片不显示

检查照片目录：

```bash
ls -lh /data/group-dashboard/photos
```

检查权限：

```bash
chown -R groupdash:groupdash /data/group-dashboard/photos
```

照片文件名要和系统里使用的身份证号或后端匹配规则一致。

### 5. 导入 Excel 后数据没变化

先确认导入动作是在后台页面完成的。Excel 不作为页面运行数据源，必须通过后台导入接口写入 H2 数据库后，页面才会显示新数据。

导入后可以重启后端确认：

```bash
systemctl restart group-dashboard
```

## 十三、最重要的迁移规则

部署或迁移时，一定带上这个文件：

```text
/opt/group-dashboard/backend/data/group-dashboard.mv.db
```

它就是当前网站的 H2 数据库。

不要只传前端页面，也不要只传 Jar。少了数据库文件，页面就会没有数据或数据不一致。
