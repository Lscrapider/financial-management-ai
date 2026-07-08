from dataclasses import dataclass
import os
from typing import Optional

from env_loader import load_env_file


def _env(name: str, default: Optional[str] = None) -> str:
    value = os.getenv(name)
    if value is None or value == "":
        if default is not None:
            return default
        raise RuntimeError(f"{name} is required")
    return value


def _int_env(name: str) -> int:
    return int(_env(name))


load_env_file()


def _minio_endpoint() -> str:
    return _env("MINIO_ENDPOINT").removeprefix("http://").removeprefix("https://")


def _minio_secure() -> bool:
    return _env("MINIO_ENDPOINT").startswith("https://")


@dataclass(frozen=True)
class RabbitMqSettings:
    host: str = _env("RABBITMQ_HOST")
    port: int = _int_env("RABBITMQ_PORT")
    username: str = _env("RABBITMQ_USERNAME")
    password: str = _env("RABBITMQ_PASSWORD")
    virtual_host: str = _env("RABBITMQ_VHOST")
    prefetch_count: int = 2
    handler_threads: int = 4
    max_attempts: int = 3
    retry_exchange: str = "finance.ocr.retry.topic"


@dataclass(frozen=True)
class PostgresSettings:
    host: str = _env("POSTGRES_HOST")
    port: int = _int_env("POSTGRES_PORT")
    database: str = _env("POSTGRES_DB")
    username: str = _env("POSTGRES_USERNAME")
    password: str = _env("POSTGRES_PASSWORD")
    options: str = "-c timezone=Asia/Shanghai"

    @property
    def dsn(self) -> str:
        return (
            f"host={self.host} port={self.port} dbname={self.database} "
            f"user={self.username} password={self.password} "
            f"options='{self.options}'"
        )


@dataclass(frozen=True)
class MinioSettings:
    endpoint: str = _minio_endpoint()
    secure: bool = _minio_secure()
    access_key: str = _env("MINIO_USER")
    secret_key: str = _env("MINIO_PASSWORD")
    artifact_bucket: str = "finance-ocr"


@dataclass(frozen=True)
class QwenOcrSettings:
    api_key: str = _env("DASHSCOPE_API_KEY")
    base_url: str = _env("DASHSCOPE_BASE_URL")
    model: str = _env("DASHSCOPE_MODEL")
    timeout_seconds: int = 120


@dataclass(frozen=True)
class DeepSeekSettings:
    api_key: str = _env("DEEPSEEK_API_KEY")
    base_url: str = _env("DEEPSEEK_BASE_URL")
    model: str = _env("DEEPSEEK_MODEL")
    thinking_type: str = "disabled"
    temperature: float = 0.3
    timeout_seconds: int = 120


@dataclass(frozen=True)
class EmbeddingSettings:
    provider: str = "local"
    model_name: str = "BAAI/bge-base-zh-v1.5"
    device: str = "cpu"
    batch_size: int = 8


@dataclass(frozen=True)
class FinanceApiSettings:
    base_url: str = _env("FINANCE_API_BASE_URL")
    timeout_seconds: int = 15


@dataclass(frozen=True)
class Settings:
    rabbitmq: RabbitMqSettings = RabbitMqSettings()
    postgres: PostgresSettings = PostgresSettings()
    minio: MinioSettings = MinioSettings()
    qwen_ocr: QwenOcrSettings = QwenOcrSettings()
    deepseek: DeepSeekSettings = DeepSeekSettings()
    embedding: EmbeddingSettings = EmbeddingSettings()
    finance_api: FinanceApiSettings = FinanceApiSettings()


settings = Settings()
