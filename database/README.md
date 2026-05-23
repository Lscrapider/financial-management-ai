# Database Services

本目录放本地数据库服务配置。前端和后端都可以本地直接跑，数据库依赖单独用这份 compose 启动。

## 启动数据库依赖

```bash
docker compose -f database/docker-compose.yml up -d
```

包含服务：

- PostgreSQL：项目业务库 `finance_management`
- database-init：启动后自动执行 `migrations` 和 `seed`
- InfluxDB：后续保存分钟线数据

## 单独启动 InfluxDB

```bash
docker compose -f database/influxdb/docker-compose.yml up -d
```

## 常用地址

- PostgreSQL：`localhost:${POSTGRES_PORT:-5432}`
- InfluxDB：`http://localhost:${INFLUXDB_PORT:-8086}`
