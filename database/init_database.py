from base64 import b64encode
from pathlib import Path
import json
import os
import sys
import time
from urllib import error, parse, request

import psycopg
from psycopg import sql


BASE_DIR = Path(__file__).resolve().parent
ROOT_DIR = BASE_DIR.parent
MIGRATIONS_DIR = BASE_DIR / "migrations"
SEED_DIR = BASE_DIR / "seed"
RABBITMQ_DEFINITIONS_FILE = BASE_DIR / "rabbitmq" / "definitions.json"

sys.path.insert(0, str(ROOT_DIR))
from env_loader import load_env_file


def load_env() -> None:
    load_env_file(ROOT_DIR)


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def required_env(name: str) -> str:
    value = env(name)
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def int_env(name: str, default: int) -> int:
    value = env(name)
    return int(value) if value else default


def required_int_env(name: str) -> int:
    return int(required_env(name))


def postgres_admin_user() -> str:
    return required_env("POSTGRES_ADMIN_USER")


def postgres_admin_password() -> str:
    return required_env("POSTGRES_ADMIN_PASSWORD")


def postgres_app_user() -> str:
    return required_env("POSTGRES_USER")


def postgres_app_password() -> str:
    return required_env("POSTGRES_PASSWORD")


def postgres_database() -> str:
    return required_env("POSTGRES_DB")


def connect_postgres(database: str, user: str, password: str):
    return psycopg.connect(
        host=required_env("POSTGRES_HOST"),
        port=required_int_env("POSTGRES_PORT"),
        user=user,
        password=password,
        dbname=database,
        autocommit=True,
        options="-c timezone=Asia/Shanghai",
    )


def wait_for_postgres() -> None:
    admin_database = required_env("POSTGRES_ADMIN_DATABASE")
    max_attempts = int_env("DATABASE_INIT_MAX_ATTEMPTS", 30)
    delay_seconds = float(env("DATABASE_INIT_RETRY_SECONDS", "2"))

    for attempt in range(1, max_attempts + 1):
        try:
            with connect_postgres(
                admin_database,
                postgres_admin_user(),
                postgres_admin_password(),
            ):
                return
        except psycopg.OperationalError as exc:
            if attempt == max_attempts:
                raise
            print(f"Waiting for PostgreSQL ({attempt}/{max_attempts}): {exc}")
            time.sleep(delay_seconds)


def init_postgres() -> None:
    database_name = postgres_database()
    admin_database = required_env("POSTGRES_ADMIN_DATABASE")
    app_user = postgres_app_user()
    app_password = postgres_app_password()

    with connect_postgres(
        admin_database,
        postgres_admin_user(),
        postgres_admin_password(),
    ) as conn:
        role_exists = conn.execute(
            "SELECT 1 FROM pg_roles WHERE rolname = %s",
            (app_user,),
        ).fetchone()
        if not role_exists:
            conn.execute(
                sql.SQL("CREATE ROLE {} LOGIN PASSWORD {}").format(
                    sql.Identifier(app_user),
                    sql.Literal(app_password),
                )
            )
        conn.execute(
            sql.SQL("ALTER ROLE {} WITH LOGIN PASSWORD {}").format(
                sql.Identifier(app_user),
                sql.Literal(app_password),
            )
        )

        exists = conn.execute(
            "SELECT 1 FROM pg_database WHERE datname = %s",
            (database_name,),
        ).fetchone()
        if not exists:
            conn.execute(
                sql.SQL("CREATE DATABASE {} OWNER {}").format(
                    sql.Identifier(database_name),
                    sql.Identifier(app_user),
                )
            )
            print(f"Database created: {database_name}")
        else:
            print(f"Database already exists: {database_name}")

    with connect_postgres(
        database_name,
        postgres_admin_user(),
        postgres_admin_password(),
    ) as conn:
        conn.execute("CREATE EXTENSION IF NOT EXISTS postgis")
        conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        conn.execute(
            sql.SQL("GRANT USAGE, CREATE ON SCHEMA public TO {}").format(
                sql.Identifier(app_user),
            )
        )
        conn.execute(
            sql.SQL("ALTER SCHEMA public OWNER TO {}").format(
                sql.Identifier(app_user),
            )
        )
        print(f"PostgreSQL extensions ensured: {database_name}")


def execute_sql_files(directory: Path) -> None:
    database_name = postgres_database()
    sql_files = sorted(directory.glob("*.sql"))
    if not sql_files:
        print(f"No SQL files found in {directory}")
        return

    with connect_postgres(
        database_name,
        postgres_app_user(),
        postgres_app_password(),
    ) as conn:
        for sql_file in sql_files:
            print(f"Executing {sql_file.relative_to(ROOT_DIR)}")
            conn.execute(sql_file.read_text(encoding="utf-8"))


def basic_auth(username: str, password: str) -> str:
    token = b64encode(f"{username}:{password}".encode()).decode()
    return f"Basic {token}"


def http_json(
    method: str,
    url: str,
    body: dict | None = None,
    headers: dict[str, str] | None = None,
    expected: tuple[int, ...] = (200, 201, 204),
) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    request_headers = {"Content-Type": "application/json"}
    if headers:
        request_headers.update(headers)

    req = request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with request.urlopen(req, timeout=15) as resp:
            payload = resp.read().decode()
            if resp.status not in expected:
                raise RuntimeError(f"{method} {url} returned {resp.status}: {payload}")
            return json.loads(payload) if payload else {}
    except error.HTTPError as exc:
        payload = exc.read().decode()
        if exc.code in expected:
            return json.loads(payload) if payload else {}
        raise RuntimeError(f"{method} {url} returned {exc.code}: {payload}") from exc


def wait_for_http(url: str, headers: dict[str, str] | None = None) -> None:
    max_attempts = int_env("DATABASE_INIT_MAX_ATTEMPTS", 30)
    delay_seconds = float(env("DATABASE_INIT_RETRY_SECONDS", "2"))

    for attempt in range(1, max_attempts + 1):
        try:
            http_json("GET", url, headers=headers)
            return
        except Exception as exc:
            if attempt == max_attempts:
                raise
            print(f"Waiting for {url} ({attempt}/{max_attempts}): {exc}")
            time.sleep(delay_seconds)


def rabbitmq_api(path: str) -> str:
    host = required_env("RABBITMQ_HOST")
    port = required_int_env("RABBITMQ_MANAGEMENT_PORT")
    return f"http://{host}:{port}/api{path}"


def rabbitmq_headers() -> dict[str, str]:
    username = required_env("RABBITMQ_ADMIN_USER")
    password = required_env("RABBITMQ_ADMIN_PASSWORD")
    return {"Authorization": basic_auth(username, password)}


def rabbitmq_path(value: str) -> str:
    return parse.quote(value, safe="")


def init_rabbitmq() -> None:
    headers = rabbitmq_headers()
    wait_for_http(rabbitmq_api("/overview"), headers=headers)

    username = required_env("RABBITMQ_USERNAME")
    password = required_env("RABBITMQ_PASSWORD")
    vhost = required_env("RABBITMQ_VHOST")

    http_json(
        "PUT",
        rabbitmq_api(f"/users/{rabbitmq_path(username)}"),
        {"password": password, "tags": ""},
        headers=headers,
    )
    http_json("PUT", rabbitmq_api(f"/vhosts/{rabbitmq_path(vhost)}"), {}, headers=headers)
    http_json(
        "PUT",
        rabbitmq_api(
            f"/permissions/{rabbitmq_path(vhost)}/{rabbitmq_path(username)}"
        ),
        {"configure": ".*", "write": ".*", "read": ".*"},
        headers=headers,
    )

    definitions = json.loads(RABBITMQ_DEFINITIONS_FILE.read_text(encoding="utf-8"))
    for exchange in definitions.get("exchanges", []):
        http_json(
            "PUT",
            rabbitmq_api(
                f"/exchanges/{rabbitmq_path(vhost)}/{rabbitmq_path(exchange['name'])}"
            ),
            {
                "type": exchange["type"],
                "durable": exchange["durable"],
                "auto_delete": exchange["auto_delete"],
                "internal": exchange.get("internal", False),
                "arguments": exchange.get("arguments", {}),
            },
            headers=headers,
        )

    for queue in definitions.get("queues", []):
        http_json(
            "PUT",
            rabbitmq_api(f"/queues/{rabbitmq_path(vhost)}/{rabbitmq_path(queue['name'])}"),
            {
                "durable": queue["durable"],
                "auto_delete": queue["auto_delete"],
                "arguments": queue.get("arguments", {}),
            },
            headers=headers,
        )

    for binding in definitions.get("bindings", []):
        destination_type = "q" if binding["destination_type"] == "queue" else "e"
        http_json(
            "POST",
            rabbitmq_api(
                f"/bindings/{rabbitmq_path(vhost)}/e/"
                f"{rabbitmq_path(binding['source'])}/{destination_type}/"
                f"{rabbitmq_path(binding['destination'])}"
            ),
            {
                "routing_key": binding.get("routing_key", ""),
                "arguments": binding.get("arguments", {}),
            },
            headers=headers,
        )

    print(f"RabbitMQ resources ensured: user={username}, vhost={vhost}")


def influxdb_headers() -> dict[str, str]:
    token = required_env("COMMON_INFLUXDB_ADMIN_TOKEN")
    return {"Authorization": f"Token {token}"}


def influxdb_url(path: str) -> str:
    return f"{required_env('INFLUXDB_URL').rstrip('/')}{path}"


def init_influxdb() -> None:
    headers = influxdb_headers()
    wait_for_http(influxdb_url("/health"))

    org_name = env("INFLUXDB_ORG", "finance")
    default_bucket = env("INFLUXDB_BUCKET", "stock_intraday")
    bucket_names = list(dict.fromkeys([
        default_bucket,
        env("INFLUXDB_STOCK_MINUTE_BUCKET", default_bucket),
        env("INFLUXDB_INDEX_MINUTE_BUCKET", "index_intraday"),
        env("INFLUXDB_BOND_MINUTE_BUCKET", "bond_intraday"),
    ]))
    token_description = env("INFLUXDB_TOKEN_DESCRIPTION", "finance application token")

    orgs = http_json(
        "GET",
        influxdb_url(f"/api/v2/orgs?org={parse.quote(org_name)}"),
        headers=headers,
        expected=(200, 404),
    ).get("orgs", [])
    if orgs:
        org_id = orgs[0]["id"]
    else:
        org = http_json(
            "POST",
            influxdb_url("/api/v2/orgs"),
            {"name": org_name},
            headers=headers,
        )
        org_id = org["id"]

    bucket_ids: list[str] = []
    for bucket_name in bucket_names:
        buckets = http_json(
            "GET",
            influxdb_url(
                f"/api/v2/buckets?name={parse.quote(bucket_name)}&orgID={org_id}"
            ),
            headers=headers,
            expected=(200, 404),
        ).get("buckets", [])
        if buckets:
            bucket_ids.append(buckets[0]["id"])
        else:
            bucket = http_json(
                "POST",
                influxdb_url("/api/v2/buckets"),
                {"orgID": org_id, "name": bucket_name, "retentionRules": []},
                headers=headers,
            )
            bucket_ids.append(bucket["id"])

    authorizations = http_json(
        "GET",
        influxdb_url(f"/api/v2/authorizations?orgID={org_id}"),
        headers=headers,
    ).get("authorizations", [])
    existing = next(
        (
            item
            for item in authorizations
            if item.get("description") == token_description
        ),
        None,
    )
    if existing is None:
        authorization = http_json(
            "POST",
            influxdb_url("/api/v2/authorizations"),
            {
                "orgID": org_id,
                "description": token_description,
                "permissions": [
                    {
                        "action": action,
                        "resource": {
                            "type": "buckets",
                            "id": bucket_id,
                            "orgID": org_id,
                        },
                    }
                    for bucket_id in bucket_ids
                    for action in ("read", "write")
                ],
            },
            headers=headers,
        )
        token = authorization.get("token")
        if token:
            print(f"InfluxDB token created. Set INFLUXDB_TOKEN={token}")

    print(f"InfluxDB resources ensured: org={org_name}, buckets={','.join(bucket_names)}")


def main() -> None:
    load_env()
    wait_for_postgres()
    init_postgres()
    execute_sql_files(MIGRATIONS_DIR)
    execute_sql_files(SEED_DIR)
    init_rabbitmq()
    init_influxdb()
    print("Finance resource initialization completed.")


if __name__ == "__main__":
    main()
