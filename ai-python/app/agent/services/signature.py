from __future__ import annotations

import base64
import hashlib
import hmac
import time
import uuid
from urllib.parse import urlparse


class AgentSignatureSigner:
    def signed_headers(
        self,
        method: str,
        url: str,
        body: bytes,
        agent_session_id: str,
        session_secret: str,
    ) -> dict[str, str]:
        timestamp = str(int(time.time() * 1000))
        nonce = uuid.uuid4().hex
        canonical = "\n".join(
            [
                method.upper(),
                urlparse(url).path,
                timestamp,
                nonce,
                hashlib.sha256(body).hexdigest(),
            ]
        )
        signature = hmac.new(
            session_secret.encode("utf-8"),
            canonical.encode("utf-8"),
            hashlib.sha256,
        ).digest()
        return {
            "X-Agent-Session-Id": agent_session_id,
            "X-Agent-Timestamp": timestamp,
            "X-Agent-Nonce": nonce,
            "X-Agent-Signature": base64.urlsafe_b64encode(signature).decode("utf-8").rstrip("="),
        }
