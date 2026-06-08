from __future__ import annotations

import logging
import re
from typing import Any

logger = logging.getLogger(__name__)


class DeepSeekToolCallAdapter:
    def parse_dsml_tool_calls(self, content: Any) -> list[dict[str, Any]]:
        text = self.normalize_dsml_text(content)
        if "||DSML||tool_calls" not in text:
            return []
        invoke_pattern = re.compile(
            r'<\|\|DSML\|\|invoke\s+name="([^"]+)"\s*>(.*?)</\|\|DSML\|\|invoke>',
            re.DOTALL,
        )
        param_pattern = re.compile(
            r'<\|\|DSML\|\|parameter\s+name="([^"]+)"[^>]*>(.*?)</\|\|DSML\|\|parameter>',
            re.DOTALL,
        )
        tool_calls = []
        for index, match in enumerate(invoke_pattern.finditer(text)):
            name = match.group(1)
            if name != "market_quote":
                continue
            args: dict[str, Any] = {}
            for param_match in param_pattern.finditer(match.group(2)):
                key = param_match.group(1)
                value = param_match.group(2).strip()
                if key == "limit":
                    args[key] = int(value) if value.isdigit() else 5
                elif value:
                    args[key] = value
            tool_calls.append({
                "id": f"dsml_market_quote_{index}",
                "name": name,
                "args": args,
            })
        logger.info("agent parsed dsml tool calls count=%s", len(tool_calls))
        return tool_calls

    def is_dsml_tool_call(self, content: Any) -> bool:
        return bool(self.parse_dsml_tool_calls(content))

    def normalize_dsml_text(self, content: Any) -> str:
        text = str(content or "").replace("｜", "|")
        text = re.sub(r"\|\s*\|\s*DSML\s*\|\s*\|", "||DSML||", text)
        text = re.sub(r"<\s*\|\|DSML\|\|", "<||DSML||", text)
        text = re.sub(r"</\s*\|\|DSML\|\|", "</||DSML||", text)
        text = re.sub(r"(<\/?\|\|DSML\|\|)\s+", r"\1", text)
        return text
