import base64
import json
from typing import Any

from openai import OpenAI

from app.core.config import QwenOcrSettings


OCR_PROMPT = """
你的任务是从用户提供的图片中识别并提取文本内容，重点关注理财、金融、股票、基金、指数、市场分析、财务指标、投资研究等相关信息。

请只返回一个 JSON 对象，不要返回 Markdown，不要返回解释文本。

JSON 格式如下：

{
  "enabled": true,
  "segments": [
    {
      "confidence": 置信度数值,
      "content": "识别出的第一段文本内容"
    }
  ]
}

输出要求：

1. 如果图片内容存在明显序号、段落、列表、标题或分块结构，请按照原有结构拆分到 segments 中。
2. 每个 segment 必须包含 confidence 和 content。
3. confidence 表示该段识别文本的置信度，取值范围为 0 到 1。
4. content 表示该段识别出的原始文本内容。
5. 保留原文中的关键数字、股票代码、指数名称、百分比、金额、日期、单位等信息。
6. 不要自行补充、推测、总结或改写图片中不存在的内容。
7. 如果图片中没有明显分段，也需要将完整识别结果作为一个 segment 返回。
8. 如果图片中没有可识别文本，返回：

{
  "enabled": false,
  "segments": []
}
""".strip()


class QwenVlOcrEngine:
    def __init__(self, settings: QwenOcrSettings) -> None:
        self._settings = settings
        self._client = OpenAI(
            api_key=settings.api_key or "missing-dashscope-api-key",
            base_url=settings.base_url,
            timeout=settings.timeout_seconds,
        )

    def recognize_png(self, image_bytes: bytes) -> dict[str, Any]:
        if not self._settings.api_key:
            raise ValueError("DASHSCOPE_API_KEY is required")
        completion = self._client.chat.completions.create(
            model=self._settings.model,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": self._to_png_data_url(image_bytes),
                            },
                        },
                        {
                            "type": "text",
                            "text": OCR_PROMPT,
                        },
                    ],
                }
            ],
        )
        content = completion.choices[0].message.content or ""
        parsed = self._parse_model_json(content)
        return {
            "rawContent": content,
            "parsed": self._normalize_parsed_result(parsed),
            "usage": completion.usage.model_dump() if completion.usage else None,
        }

    def _to_png_data_url(self, image_bytes: bytes) -> str:
        encoded = base64.b64encode(image_bytes).decode("ascii")
        return f"data:image/png;base64,{encoded}"

    def _parse_model_json(self, content: str) -> dict[str, Any]:
        payload = json.loads(self._strip_json_fence(content))
        if not isinstance(payload, dict):
            raise ValueError("OCR model response is not a JSON object")
        return payload

    def _strip_json_fence(self, content: str) -> str:
        stripped = content.strip()
        if not stripped.startswith("```"):
            return stripped
        lines = stripped.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        return "\n".join(lines).strip()

    def _normalize_parsed_result(self, payload: dict[str, Any]) -> dict[str, Any]:
        enabled = bool(payload.get("enabled"))
        segments = payload.get("segments") or []
        if not isinstance(segments, list):
            raise ValueError("OCR model response segments must be a list")
        normalized_segments: list[dict[str, Any]] = []
        for index, segment in enumerate(segments, start=1):
            if not isinstance(segment, dict):
                raise ValueError("OCR model segment must be a JSON object")
            content = str(segment.get("content") or "").strip()
            confidence = float(segment.get("confidence") or 0)
            normalized_segments.append(
                {
                    "segmentNo": index,
                    "confidence": max(0, min(confidence, 1)),
                    "content": content,
                }
            )
        if not normalized_segments:
            enabled = False
        return {
            "enabled": enabled,
            "segments": normalized_segments,
        }
