# 高校宿舍楼智能管理系统（Group1）

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js（含 npm / npx）
- MySQL 8.0+

## 数据库初始化 / 同步

### 全新建库（推荐）

在 MySQL Shell 中执行：

```sql
\sql
\connect root@localhost:3306
DROP DATABASE IF EXISTS smart_dorm;
\source "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\backend\sql\init.sql"
```

### 旧库增量补齐（不清库）

如果你的 `smart_dorm` 已经是新结构（能看到 `sys_user_student` 表），但缺少卫生/冲突预警等新增表，可执行：

```sql
\sql
\connect root@localhost:3306
USE smart_dorm;
\source "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\backend\sql\init.sql"
```

说明：`init.sql` 使用 `CREATE TABLE IF NOT EXISTS`，不会删除既有表与数据，只会补齐缺表/缺数据（例如卫生规则）。

## 启动后端（jar）

建议使用 jar 方式启动（Windows 下更稳定）：

```powershell
cd "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\backend\backend"
mvn -DskipTests clean package
java -jar target\backend-0.0.1-SNAPSHOT.jar
```

健康检查：

```powershell
curl http://127.0.0.1:8080/api/health
```

上传文件访问：

- 上传接口：`POST http://127.0.0.1:8080/api/files/upload`（multipart/form-data，字段名 `file`）
- 图片静态访问：`http://127.0.0.1:8080/uploads/<filename>`

## 启动前端（全部使用 127.0.0.1）

前端工程目录：`SmartDorm\group1\frontend`

每个项目各开一个终端执行（首次需要 `npm install`）。

### 统一门户（5173）

```powershell
cd "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\frontend\web-portal"
npm install
npx vite --host 127.0.0.1 --port 5173
```

### 学生/宿舍长端（5174）

```powershell
cd "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\frontend\web-student-leader"
npm install
npx vite --host 127.0.0.1 --port 5174
```

### 宿管端（5175）

```powershell
cd "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\frontend\web-dorm-manager"
npm install
npx vite --host 127.0.0.1 --port 5175
```

### 系统管理员端（5176）

```powershell
cd "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\frontend\web-sys-admin"
npm install
npx vite --host 127.0.0.1 --port 5176
```

### 宿舍内部管理端（5177）

```powershell
cd "d:\mine\bjtu\专业实训IV\高校宿舍楼智能管理系统\SmartDorm\group1\frontend\web-internal-manage"
npm install
npx vite --host 127.0.0.1 --port 5177
```

入口：

- 统一门户：`http://127.0.0.1:5173/`

## Gerrit 提交

在仓库根目录（`SmartDorm\group1`）执行：

```powershell
git status
git add -A
git commit -m "Your change summary"
git push origin HEAD:refs/for/master
```

如果提示缺少 `Change-Id`：

```powershell
git commit --amend --no-edit
git push origin HEAD:refs/for/master
```
