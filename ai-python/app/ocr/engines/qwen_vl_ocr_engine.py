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
      "content": "识别出的第一段正文内容"
    }
  ]
}

正文抽取要求：
1. 只保留图片中的正文内容，优先提取与理财、金融、股票、基金、指数、市场分析、财务指标、投资研究相关的正文段落。
2. 请去除引用、页眉、页脚、页码、文档标题重复项、章节页眉、网站导航栏、菜单栏、按钮文字、水印、版权声明、免责声明、来源栏、二维码说明、广告语、无关装饰性文字。
3. 请去除目录内容，例如“目录”“Contents”下的章节列表、页码索引、跳转链接等。
4. 请去除表格内容，不要输出表格中的单元格文字、表头、行列数据，也不要尝试将表格转成文本。
5. 请去除公式内容，不要输出数学公式、计算公式、LaTeX 公式、单独的等式或推导过程。
6. 请去除图表标题、图例、坐标轴文字、数据标签、图注、图片说明、脚注、参考文献、引用文献列表。
7. 如果正文中自然包含关键数字、股票代码、指数名称、百分比、金额、日期、单位、财务指标等信息，请完整保留。
8. 如果一个数字或代码只出现在表格、图表、坐标轴、图例、公式、页眉页脚中，请不要提取。
9. 如果页面中存在重复出现的标题、公司名称、报告名称、日期、页码等内容，并且明显属于页眉或页脚，请不要提取。
10. 如果正文段落中包含引用标记，例如 “[1]”“[2]”“(Smith, 2020)” 或上标编号，可以保留正文句子，但不要单独输出参考文献列表。

分段要求：
1. 如果图片内容存在明显序号、段落、列表、标题或分块结构，请按照原有结构拆分到 segments 中。
2. 每个 segment 必须包含 confidence 和 content。
3. confidence 表示该段识别文本的置信度，取值范围为 0 到 1。
4. content 表示该段识别出的原始正文文本内容。
5. 保留原文中的关键数字、股票代码、指数名称、百分比、金额、日期、单位等信息。
6. 不要自行补充、推测、总结、改写或翻译图片中不存在的内容。
7. 不要输出被过滤掉的页眉、页脚、标题重复项、表格、公式、图表、图注、脚注、参考文献等内容。
8. 如果图片中没有明显分段，也需要将完整识别到的正文内容作为一个 segment 返回。
9. 如果图片中只有页眉页脚、标题、目录、表格、公式、图表、图注、参考文献、广告、水印或其他非正文内容，则返回 enabled 为 false。
10. 如果图片中没有可识别正文文本，返回：
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
