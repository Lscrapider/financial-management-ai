from dataclasses import dataclass
import os


def _int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    return int(value)


@dataclass(frozen=True)
class RabbitMqSettings:
    host: str = os.getenv("RABBITMQ_HOST", "localhost")
    port: int = _int_env("RABBITMQ_PORT", 5672)
    username: str = os.getenv("RABBITMQ_USERNAME", "finance")
    password: str = os.getenv("RABBITMQ_PASSWORD", "finance123456")
    virtual_host: str = os.getenv("RABBITMQ_VHOST", "finance")
    prefetch_count: int = _int_env("WORKER_PREFETCH_COUNT", 2)
    handler_threads: int = _int_env("WORKER_HANDLER_THREADS", 4)
    max_attempts: int = _int_env("WORKER_MAX_ATTEMPTS", 3)
    retry_exchange: str = os.getenv("OCR_RETRY_EXCHANGE", "finance.ocr.retry.topic")


@dataclass(frozen=True)
class PostgresSettings:
    host: str = os.getenv("POSTGRES_HOST", "localhost")
    port: int = _int_env("POSTGRES_PORT", 5432)
    database: str = os.getenv("POSTGRES_DB", "finance_management")
    username: str = os.getenv("POSTGRES_USERNAME", os.getenv("POSTGRES_USER", "postgres"))
    password: str = os.getenv("POSTGRES_PASSWORD", "123456")

    @property
    def dsn(self) -> str:
        return (
            f"host={self.host} port={self.port} dbname={self.database} "
            f"user={self.username} password={self.password}"
        )


@dataclass(frozen=True)
class MinioSettings:
    endpoint: str = os.getenv("MINIO_ENDPOINT", "localhost:9000").removeprefix("http://").removeprefix("https://")
    secure: bool = os.getenv("MINIO_SECURE", "false").lower() == "true"
    access_key: str = os.getenv("MINIO_ROOT_USER", "finance")
    secret_key: str = os.getenv("MINIO_ROOT_PASSWORD", "finance123456")
    artifact_bucket: str = os.getenv("MINIO_OCR_BUCKET", "finance-ocr")


@dataclass(frozen=True)
class QwenOcrSettings:
    api_key: str = os.getenv("DASHSCOPE_API_KEY", "sk-1ef7eabc3eae4b439de96141b505462d")
    base_url: str = os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    model: str = os.getenv("QWEN_OCR_MODEL", "qwen-vl-ocr-latest")
    timeout_seconds: int = _int_env("QWEN_OCR_TIMEOUT_SECONDS", 120)


@dataclass(frozen=True)
class EmbeddingSettings:
    provider: str = os.getenv("EMBEDDING_PROVIDER", "local")
    model_name: str = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-small-zh-v1.5")
    device: str = os.getenv("EMBEDDING_DEVICE", "cpu")
    batch_size: int = _int_env("EMBEDDING_BATCH_SIZE", 8)


@dataclass(frozen=True)
class Settings:
    rabbitmq: RabbitMqSettings = RabbitMqSettings()
    postgres: PostgresSettings = PostgresSettings()
    minio: MinioSettings = MinioSettings()
    qwen_ocr: QwenOcrSettings = QwenOcrSettings()
    embedding: EmbeddingSettings = EmbeddingSettings()


settings = Settings()
