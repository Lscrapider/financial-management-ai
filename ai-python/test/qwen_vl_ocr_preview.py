import argparse
import base64
import json
import mimetypes
import os
from pathlib import Path
from typing import Any

from openai import OpenAI


OCR_PROMPT = """
你的任务是从用户提供的图片中识别并提取文本内容，重点关注理财、金融、股票、基金、指数、市场分析、财务指标、投资研究等相关信息。

请只返回一个 JSON 对象，不要返回 Markdown，不要返回解释文本。

JSON 格式如下：

{
  "enabled": true,
  "segments": [
    {
      "confidence": 表示置信度,
      "content": "识别出的第一段文本内容"
    }
  ]
}

输出要求：

1. 如果图片内容存在明显序号、段落、列表、标题或分块结构，请按照原有结构拆分到 segments 中。
2. 每个 segment 必须包含 confidence 和 content。
3. confidence 表示该段识别文本的置信度，取值范围为 0 到 1。
4. content 表示该段识别出的原始文本内容。
5. 保留图片中的完整信息。
6. 不要自行补充、推测、总结或改写图片中不存在的内容。
7. 如果图片中没有明显分段，也需要将完整识别结果作为一个 segment 返回。
8. 如果图片中没有可识别文本，返回：

{
  "enabled": false,
  "segments": []
}
""".strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Qwen VL OCR preview for one local image.")
    parser.add_argument("file_path", help="Local image file path, for example /tmp/page-001.png")
    parser.add_argument("--raw", action="store_true", help="Print raw model content instead of parsed JSON.")
    return parser.parse_args()


def image_to_data_url(file_path: Path) -> str:
    mime_type = mimetypes.guess_type(file_path.name)[0] or "image/png"
    encoded = base64.b64encode(file_path.read_bytes()).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


def strip_json_fence(content: str) -> str:
    stripped = content.strip()
    if not stripped.startswith("```"):
        return stripped
    lines = stripped.splitlines()
    if lines and lines[0].startswith("```"):
        lines = lines[1:]
    if lines and lines[-1].strip() == "```":
        lines = lines[:-1]
    return "\n".join(lines).strip()


def parse_model_json(content: str) -> dict[str, Any]:
    payload = json.loads(strip_json_fence(content))
    if not isinstance(payload, dict):
        raise ValueError("model response is not a JSON object")
    return payload


def run_ocr(file_path: Path) -> dict[str, Any]:
    if not file_path.exists():
        raise FileNotFoundError(f"文件不存在: {file_path}")
    if not file_path.is_file():
        raise ValueError(f"路径不是文件: {file_path}")

    client = OpenAI(
        api_key="sk-1ef7eabc3eae4b439de96141b505462d",
        base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    )
    completion = client.chat.completions.create(
        model="qwen-vl-ocr-latest",
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": image_to_data_url(file_path),
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
    return {
        "filePath": str(file_path),
        "content": content,
        "parsed": parse_model_json(content),
        "usage": completion.usage.model_dump() if completion.usage else None,
    }


def main() -> None:
    result = run_ocr(Path('/Users/qinzeyu/Downloads/知识库1.jpg').expanduser().resolve())
    print(result["content"])
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
