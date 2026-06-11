from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.services.data_gateway_client import AgentDataGatewayClient
from app.core.config import settings
from app.ocr.engines.embedding_engine import SentenceTransformersEngine

logger = logging.getLogger(__name__)

KNOWLEDGE_TAG_SCENES = (
    "asset",
    "price",
    "volume",
    "trend",
    "valuation",
    "sentiment",
    "risk_strategy",
)


class LocalEmbeddingProvider:
    def __init__(self) -> None:
        self._engine: SentenceTransformersEngine | None = None

    def embed_query(self, text: str) -> list[float]:
        if self._engine is None:
            self._engine = SentenceTransformersEngine(
                settings.embedding.model_name,
                settings.embedding.device,
                settings.embedding.batch_size,
            )
        embeddings = self._engine.embed([text])
        return embeddings[0] if embeddings else []


class KnowledgeSearchTool:
    def __init__(
        self,
        data_gateway_client: AgentDataGatewayClient | None = None,
        embedding_provider: Any | None = None,
    ) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self._embedding_provider = embedding_provider or LocalEmbeddingProvider()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        query_text: str,
        scenes: list[str] | None = None,
        tags: dict[str, Any] | None = None,
        limit: int = 5,
    ) -> str:
        self.last_result = self.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            query_text=query_text,
            scenes=scenes,
            tags=tags,
            limit=limit,
        )
        return json.dumps(self.last_result, ensure_ascii=False, default=str)

    def query(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        query_text: str,
        scenes: list[str] | None = None,
        tags: dict[str, Any] | None = None,
        limit: int = 5,
    ) -> dict[str, Any]:
        normalized_query_text = str(query_text or "").strip()
        if not normalized_query_text:
            return {"chunks": []}
        normalized_limit = max(1, min(int(limit or 5), 8))
        normalized_scenes = _normalize_scenes(scenes)
        params: dict[str, Any] = {
            "queryText": normalized_query_text,
            "queryEmbedding": self._embedding_provider.embed_query(normalized_query_text),
            "scenes": normalized_scenes,
            "tags": _normalize_tags(tags, normalized_scenes),
            "limit": normalized_limit,
        }
        logger.info(
            "agent tool knowledge_search invoke session_id=%s scenes=%s limit=%s query_preview=%s",
            agent_session_id,
            params["scenes"],
            normalized_limit,
            normalized_query_text[:120],
        )
        response = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="knowledge.search",
            params=params,
            limit=normalized_limit,
        )
        rows = response.get("data") if isinstance(response, dict) else []
        chunks = [self._llm_chunk(row) for row in rows if isinstance(row, dict)]
        chunks = [chunk for chunk in chunks if chunk]
        return {"chunks": chunks}

    def _llm_chunk(self, row: dict[str, Any]) -> dict[str, str]:
        filename = str(row.get("filename") or row.get("sourceName") or "").strip()
        content = str(row.get("content") or row.get("text") or "").strip()
        if not content:
            return {}
        return {
            "filename": filename,
            "content": content,
        }


def _normalize_scenes(scenes: list[str] | None) -> list[str]:
    return [str(scene).strip() for scene in scenes or [] if str(scene).strip()]


def _normalize_tags(tags: dict[str, Any] | None, scenes: list[str]) -> dict[str, list[str]]:
    if not isinstance(tags, dict):
        return {}
    normalized: dict[str, list[str]] = {}
    loose_tags: list[str] = []
    for key, value in tags.items():
        key_text = str(key).strip()
        if not key_text:
            continue
        if isinstance(value, list):
            values = _string_list(value)
            if values:
                normalized[key_text] = values
            continue
        if isinstance(value, str):
            value_text = value.strip()
            if value_text:
                normalized[key_text] = [value_text]
            continue
        if isinstance(value, bool):
            if value:
                loose_tags.append(key_text)
            continue
        if value:
            loose_tags.append(key_text)

    if loose_tags:
        target_scenes = scenes or list(KNOWLEDGE_TAG_SCENES)
        for scene in target_scenes:
            values = normalized.setdefault(scene, [])
            for tag in loose_tags:
                if tag not in values:
                    values.append(tag)
    return {scene: values for scene, values in normalized.items() if values}


def _string_list(values: list[Any]) -> list[str]:
    result: list[str] = []
    for value in values:
        text = str(value).strip()
        if text and text not in result:
            result.append(text)
    return result
