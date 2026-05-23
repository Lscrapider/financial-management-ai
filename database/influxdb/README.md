# InfluxDB Local Service

分钟级股票走势数据后续会写入 InfluxDB。当前先提供本地容器服务，Java 接入逻辑后续再改。

## 配置

配置项放在项目根目录 `.env`：

```env
INFLUXDB_PORT=8086
INFLUXDB_USERNAME=admin
INFLUXDB_PASSWORD=admin123456
INFLUXDB_ORG=finance
INFLUXDB_BUCKET=stock_intraday
INFLUXDB_ADMIN_TOKEN=finance-management-local-token
INFLUXDB_URL=http://localhost:8086
```

## 单独启动 InfluxDB

```bash
docker compose -f database/influxdb/docker-compose.yml up -d
```

## 随项目数据库一起启动

```bash
docker compose -f docker/docker-compose.yml up -d influxdb
```

## 访问

- Web UI: `http://localhost:8086`
- Organization: `finance`
- Bucket: `stock_intraday`
