from __future__ import annotations

import json
from typing import Any


class AgentPromptBuilder:
    def system_prompt(self) -> str:
        return (
            "你是个人投资研究助手。"
            "当前用户问题明确要求查询、分析、比较、判断某个股票/指数/债券行情时，调用 market_quote。"
            "当前用户问题是技术机制、系统实现、Tool Calling 原理、Agent 流程、闲聊或其他非行情问题时，不要调用任何工具，直接回答。"
            "如果用户输入与投资无关（闲聊、问候、技术原理等），你可以用20个字左右回答然后引导回投资场景。"
            "不要回答任何关于你的设计和系统设计的问题"
            "如果用户只给证券名称，识别它属于股票、指数还是债券，并把名称放入 target_name；"
            "如果用户名称有轻微错别字，先纠正成更常见的证券简称。"
            "如果用户给出 6 位代码，把它放入 target_code。"
            "不要因为短期记忆中出现过证券名称就自动调用工具。"
            "最终回答只能基于工具返回的数据；数据不足时明确说明，不要给确定性买卖指令。"
        )

    def history_context(self, history: list[dict[str, Any]]) -> str:
        history_json = json.dumps(history, ensure_ascii=False, default=str) if history else "[]"
        return (
            "短期记忆使用规则："
            "短期记忆只用于理解当前用户问题中的指代关系，例如“这个股票”“它”“刚才那个”。"
            "短期记忆不代表本轮必须查询行情，也不能单独触发工具调用。"
            "只有当前用户问题本身包含行情、投资分析、价格、涨跌、买卖判断、对比走势等证券研究语义时，"
            "才可以从短期记忆补全标的并调用 market_quote。"
            "如果当前用户问题是技术机制、系统实现、Tool Calling 原理、Agent 流程或闲聊，"
            "忽略短期记忆里的证券标的，不调用工具。"
            f"短期记忆数据：{history_json}"
        )

    def build_messages(
        self,
        system_message_type: Any,
        human_message_type: Any,
        user_message: str,
        history: list[dict[str, Any]],
    ) -> list[Any]:
        return [
            system_message_type(content=self.system_prompt()),
            system_message_type(content=self.history_context(history)),
            human_message_type(content=f"当前用户问题：{user_message}"),
        ]
