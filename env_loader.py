import os
from pathlib import Path
from typing import Optional


SKIP_ENV_FILE_NAME = "URBAN_SIDEQUEST_SKIP_ENV_FILE"
ENV_FILE_NAME = "FINANCE_ENV_FILE"


def load_env_file(root_dir: Optional[Path] = None) -> None:
    base_dir = root_dir or Path(__file__).resolve().parent
    explicit_env_file = os.getenv(ENV_FILE_NAME)
    if explicit_env_file:
        candidates = [Path(explicit_env_file)]
    elif os.getenv(SKIP_ENV_FILE_NAME) == "1":
        candidates = [base_dir / ".env.dev"]
    else:
        candidates = [base_dir / ".env", base_dir / ".env.dev"]

    for env_path in candidates:
        if not env_path.exists():
            continue
        for line in env_path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            key, value = stripped.split("=", 1)
            value = value.strip().strip('"').strip("'")
            if not value:
                continue
            os.environ.setdefault(key.strip(), value)
