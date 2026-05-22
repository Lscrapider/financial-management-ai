from pathlib import Path
import os
import time

import psycopg
from psycopg import sql


BASE_DIR = Path(__file__).resolve().parent
ROOT_DIR = BASE_DIR.parent
MIGRATIONS_DIR = BASE_DIR / "migrations"
SEED_DIR = BASE_DIR / "seed"


def load_env() -> None:
    env_path = ROOT_DIR / ".env"
    if not env_path.exists():
        return

    for line in env_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def env(name: str, default: str) -> str:
    return os.environ.get(name, default)


def connect(database: str):
    return psycopg.connect(
        host=env("POSTGRES_HOST", "localhost"),
        port=int(env("POSTGRES_PORT", "5432")),
        user=env("POSTGRES_USER", "postgres"),
        password=env("POSTGRES_PASSWORD", "postgres"),
        dbname=database,
        autocommit=True,
    )


def wait_for_postgres() -> None:
    admin_database = env("POSTGRES_ADMIN_DATABASE", "postgres")
    max_attempts = int(env("DATABASE_INIT_MAX_ATTEMPTS", "30"))
    delay_seconds = float(env("DATABASE_INIT_RETRY_SECONDS", "2"))

    for attempt in range(1, max_attempts + 1):
        try:
            with connect(admin_database):
                return
        except psycopg.OperationalError as exc:
            if attempt == max_attempts:
                raise
            print(f"Waiting for PostgreSQL ({attempt}/{max_attempts}): {exc}")
            time.sleep(delay_seconds)


def create_database() -> None:
    database_name = env("POSTGRES_DB", "finance_management")
    admin_database = env("POSTGRES_ADMIN_DATABASE", "postgres")

    with connect(admin_database) as conn:
        exists = conn.execute(
            "SELECT 1 FROM pg_database WHERE datname = %s",
            (database_name,),
        ).fetchone()
        if exists:
            print(f"Database already exists: {database_name}")
            return

        conn.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))
        print(f"Database created: {database_name}")


def execute_sql_files(directory: Path) -> None:
    database_name = env("POSTGRES_DB", "finance_management")
    sql_files = sorted(directory.glob("*.sql"))
    if not sql_files:
        print(f"No SQL files found in {directory}")
        return

    with connect(database_name) as conn:
        for sql_file in sql_files:
            print(f"Executing {sql_file.relative_to(ROOT_DIR)}")
            conn.execute(sql_file.read_text(encoding="utf-8"))


def main() -> None:
    load_env()
    wait_for_postgres()
    create_database()
    execute_sql_files(MIGRATIONS_DIR)
    execute_sql_files(SEED_DIR)
    print("Database initialization completed.")


if __name__ == "__main__":
    main()
