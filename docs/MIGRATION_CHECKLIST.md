# 项目迁移清单

迁移项目时，请完整保留本目录：`/Users/geckomoria/Documents/群体网站`。

## 必须迁移的源码目录

- `frontend/`：Vue 前端页面、后台管理页面、样式和本地前端依赖。
- `backend/`：Spring Boot 后端、接口、Excel 导入、MySQL 数据读写逻辑。
- `tests/`：前端页面和数据迁移相关测试。

## 前端关键文件

- `frontend/index.html`：首页数据分析页面。
- `frontend/admin.html`：后台管理页面。
- `frontend/src/admin.js`：后台人员列表、个人详情、导入数据、资金图谱交互。
- `frontend/src/app.js`：首页图表、人员列表、PDF 导出等交互。
- `frontend/src/admin-helpers.js`：后台个人详情、照片、关联人等辅助逻辑。
- `frontend/src/api.js`：前端请求后端 API 的地址处理。
- `frontend/src/data.js`：前端兜底演示数据。
- `frontend/src/styles.css`：首页和后台全部样式。
- `frontend/public/vendor/`：Vue、Element Plus 本地静态依赖。

## 后端关键文件

- `backend/pom.xml`：Spring Boot、POI、MySQL、测试依赖。
- `backend/src/main/java/com/example/groupdashboard/GroupDashboardApplication.java`：后端启动入口。
- `backend/src/main/java/com/example/groupdashboard/AdminPeopleController.java`：后台人员接口、Excel 导入接口。
- `backend/src/main/java/com/example/groupdashboard/AdminPeopleService.java`：后台人员业务逻辑。
- `backend/src/main/java/com/example/groupdashboard/DashboardDatabaseStore.java`：MySQL 人员数据存取。
- `backend/src/main/java/com/example/groupdashboard/DashboardFundRelationService.java`：资金关系 Excel 入库和资金图谱生成。
- `backend/src/main/java/com/example/groupdashboard/AdminFundRelationController.java`：资金关系导入和图谱接口。
- `backend/src/main/java/com/example/groupdashboard/ExcelDashboardService.java`：Excel 解析和首页统计。
- `backend/src/main/java/com/example/groupdashboard/ApiCorsConfig.java`：前后端跨域配置。
- `backend/src/main/java/com/example/groupdashboard/PhotoController.java`：照片读取接口。
- `backend/src/main/resources/application.yml`：默认配置。
- `backend/src/main/resources/application-mysql.yml`：MySQL 配置。
- `backend/src/test/java/com/example/groupdashboard/AdminPeopleControllerTest.java`：后端导入和资金图谱测试。

## 不建议只迁移的目录

- 不要只拷 `target/`，这是编译输出，不是完整源码。
- 根目录不再保留 `admin.html/index.html/*.js/styles.css/vendor/target` 这类旧入口和生成产物；迁移时不要从根目录启动静态页面。
- 页面入口只认 `frontend/admin.html` 和 `frontend/index.html`，后端入口只认 `backend/pom.xml`。
- `.idea/` 可迁移也可不迁移，它只是 IntelliJ 配置。

## 启动方式

后端：

```bash
cd /Users/geckomoria/Documents/群体网站/backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

前端：

```bash
cd /Users/geckomoria/Documents/群体网站/frontend
python3 -m http.server 5174
```

访问后台：

```text
http://localhost:5174/admin.html
```

访问首页：

```text
http://localhost:5174/index.html
```

## 数据库和导入

- 人员 Excel 通过后台“导入数据”进入 MySQL。
- 资金关系 Excel 通过后台“导入资金关系”进入 MySQL。
- 照片通过后台“导入照片”进入照片接口数据。
- 迁移后如果数据库是新的，需要重新在后台导入 Excel 和照片。

## 最近已保存的重要修改

- 后台资金关系支持导入 Excel 入库。
- 后台支持导入关联人、隐名投资人、增加人员 Excel；三类数据分别落入独立数据库表：`dashboard_import_related_person`、`dashboard_import_hidden_investor`、`dashboard_import_added_person`，对应字段表为同名加 `_column`。
- 资金图谱关系从数据库读取资金关系表生成。
- 资金图谱只显示当前个人身份证号对应的显名投资人。
- 资金图谱不再显示其他显名投资人。
- 同层级人员之间不再绘制虚线，只保留相邻层级资金流向。
- 资金图谱弹窗展示板变大，节点变小，金额显示在人名下面。
- 首页个人详情已统一使用后端资金关系图谱接口，不再保留旧的静态四级资金占位图。
- 首页个人详情去掉多余的通信图谱按钮，只保留资金图谱关系。
- 个人详情里的“背后是否存在隐性投资人”会根据资金图谱自动覆盖：只要资金图谱存在下层人员，就显示“有”。
- 资金图谱节点金额统一显示为“向上层投资金额：xxx万”，金额按万元取整，不显示小数。
- 资金图谱节点已加宽，避免金额文字裁切；不同层级节点使用不同颜色区分。
- 四个等级的导出打印为 A3 横版彩色单页版，人员卡片带真实照片，并按人数自动压缩排版。
- 一般参与人员背景统一为沉稳蓝色，密切关注人员背景统一为绿色，列表和导出打印保持一致。
- 首页“一般参与人员 / 密切关注人员”分类按钮保持等宽，副标题小字统一放在大标题下方。
- 首页地区/区县统计块支持点击打开对应人员列表，列表通过后端 `region` 参数按数据库属地精准筛选。
- 本市区县分布只按 Excel `省内人员简易户籍` 字段识别哈尔滨区县，不再用属地、户籍地址、派出所地址兜底归入本市区县；第二行只统计该字段能识别出的黑龙江非哈尔滨地市，外省市不进入两行统计。
- 地区/区县点击列表只匹配后端规范化后的 `district` 字段，保证按钮外显人数和点进去列表总人数一致。
- 首页投资金额比例按“持有中融信托产品份额总数”分档统计。
- 投资金额比例只统计本省数据。
- 点击饼图可进入对应人员列表和个人详情。
- 无包保责任人的个人详情不显示包保责任人。
- “前科累计情况”和“ZR被处置打击人员”仅有值时显示在个人信息里。

## 验证命令

前端测试：

```bash
node --test tests/dashboard.test.mjs tests/admin-migration.test.mjs
```

后端资金图谱测试：

```bash
cd backend
mvn test -Dtest=AdminPeopleControllerTest#layeredFundRelationGraphOnlyUsesCurrentVisibleInvestorAndAdjacentLayers
```
