from __future__ import annotations

from typing import Any

from app.agent.graph.state import AgentGraphState


class FinalAnswerPromptBuilder:
    def build_messages(self, state: AgentGraphState) -> list[Any]:
        system_message_type, human_message_type = self._message_types(state)
        if system_message_type is None or human_message_type is None:
            return []
        return [
            system_message_type(content=self.system_prompt()),
            human_message_type(content=self.user_prompt(state)),
        ]

    def system_prompt(self) -> str:
        return (
            "你是个人投资研究助手的最终回答生成器。\n\n"
            "当前阶段只负责生成给用户看的最终答复，不负责工具规划、数据查询或补充证据。\n"
            "所有工具调用、数据查询和证据收集都已经结束。\n\n"
            "输出边界：\n"
            "- 只能输出中文自然语言正文。\n"
            "- 不得调用工具，不得要求继续调用工具。\n"
            "- 不得输出 DSML、tool_calls、invoke、JSON 工具调用片段。\n"
            "- 不得提及内部工具名、工具调用过程、系统实现或内部接口。\n"
            "- 不得暴露缓存 key、chunkId、taskNo、检索分数、queryText、tags 等内部元数据。\n"
            "- 不得原样输出任何数据字段名、JSON key、英文驼峰字段、下划线字段或内部枚举值。\n"
            "- 如果需要表达字段含义，必须转换成用户能理解的中文自然表达，例如把 latestPrice 表达为“最新价”，把 changePercent 表达为“涨跌幅”，不要输出字段名本身。\n"
            "- 不得补充没有证据支持的数据、结论或具体数值。\n\n"
            "回答原则：\n"
            "- 只能基于用户问题、用户画像、短期记忆和已提供证据回答。\n"
            "- 可以对证据做归纳、比较和风险判断，但结论必须能从证据中推出。\n"
            "- 如果证据不足，必须明确说明不足，并给出保守结论、观察条件或风险提示。\n"
            "- 交易建议由当前用户问题、用户画像建议边界和证据共同决定；不要只因为用户没写“买卖建议”就回避交易决策类问题。\n"
            "- 当用户问题包含能不能买、会不会涨、该不该卖、怎么办、仓位等交易决策意图时，如果用户画像允许且证据支持，可以按画像边界给出条件、仓位边界和退出条件；没有可用用户画像时，不给明确买卖指令。\n"
            "- 当用户只是泛看或资料查询，且没有交易决策意图时，不主动给买入、卖出、加仓、减仓、仓位比例、止盈止损价位等交易指令。\n"
            "- 回答应直接面向用户，不要说“根据工具返回”“我调用了某工具”“系统数据显示”。"
        )

    def user_prompt(self, state: AgentGraphState) -> str:
        return (
            f"【当前用户问题】\n{self._user_question(state)}\n\n"
            f"【用户画像】\n{self._psych_profile_text(state.get('psych_profile'))}\n\n"
            f"【短期记忆】\n{self._memory_text(state.get('memory_context'))}\n\n"
            f"【证据充分性判断】\n{self._decision_text(state)}\n\n"
            f"【流程停止原因】\n{self._stop_reason_text(state.get('stop_reason'))}\n\n"
            f"【已获取证据】\n{self._evidence_text(state)}\n\n"
            "要求：\n"
            "- 必须正面回答【当前用户问题】，不要绕开问题或只罗列资料。(重点要求)\n"
            "- 只使用【已获取证据】中的内容。\n"
            "- 没有证据覆盖的维度不要展开。\n"
            "- 不要补充没有证据支持的基本面、估值、场景信号、知识库结论或买卖建议。\n"
            "- 不要提及内部工具名、工具调用过程、DSML、tool_calls、invoke 或系统实现。\n"
            "- 如果数据之间存在冲突，明确指出冲突，并以保守方式表达。"
        )

    def _message_types(self, state: AgentGraphState) -> tuple[type | None, type | None]:
        messages = state.get("messages") or []
        if len(messages) >= 2:
            return type(messages[0]), type(messages[-1])
        if len(messages) == 1:
            return type(messages[0]), type(messages[0])
        planning_message = state.get("planning_message")
        if planning_message is not None:
            return type(planning_message), type(planning_message)
        return None, None

    def _user_question(self, state: AgentGraphState) -> str:
        messages = state.get("messages") or []
        if not messages:
            return "无"
        content = str(getattr(messages[-1], "content", "") or "").strip()
        prefix = "当前用户问题："
        if content.startswith(prefix):
            content = content[len(prefix):].strip()
        return content or "无"

    def _psych_profile_text(self, profile: dict[str, Any] | None) -> str:
        if not profile:
            return (
                "状态：无可用用户画像\n"
                "建议边界：不能给出明确买卖指令，只能给风险提示、观察条件或保守结论。\n"
                "画像摘要：无"
            )
        advice_style = str(profile.get("adviceStyle") or "no_trade_advice")
        holding_mindset = profile.get("holdingMindset")
        if isinstance(holding_mindset, list):
            holding_mindset_text = "、".join(str(item) for item in holding_mindset)
        else:
            holding_mindset_text = str(holding_mindset or "未识别")
        return (
            "状态：已加载\n"
            f"建议边界：{self._advice_boundary_text(advice_style)}\n"
            f"画像摘要：{profile.get('summary') or '无'}\n"
            f"风险情绪：{profile.get('riskEmotion') or '未识别'}\n"
            f"决策风格：{profile.get('decisionStyle') or '未识别'}\n"
            f"操作节奏：{profile.get('tradingTempo') or '未识别'}\n"
            f"持仓心态：{holding_mindset_text}"
        )

    def _advice_boundary_text(self, advice_style: str) -> str:
        mapping = {
            "risk_first": "risk_first，只能给风险提示和观察条件，不能直接给买入仓位。",
            "no_trade_advice": "no_trade_advice，不能给出明确买卖指令，只能给风险提示、观察条件或保守结论。",
            "conditional_trade": "conditional_trade，只能给条件建议，不能直接说现在买入固定仓位。",
            "explicit_trade_light_position": "explicit_trade_light_position，可以给明确买卖，但仓位必须限制在轻仓试错区间。",
        }
        return mapping.get(advice_style, f"{advice_style}，按保守边界表达，不给无证据支持的交易指令。")

    def _memory_text(self, memory_context: str | None) -> str:
        text = str(memory_context or "").strip()
        return text if text else "无"

    def _decision_text(self, state: AgentGraphState) -> str:
        decision = state.get("final_decision")
        if decision is None:
            return "状态：无\n原因：无"
        return (
            f"状态：{decision.status}\n"
            f"原因：{decision.reason or '无'}"
        )

    def _stop_reason_text(self, stop_reason: str | None) -> str:
        if not stop_reason:
            return "无"
        reason_map = {
            "tool_budget_exhausted": "工具预算已耗尽，不能继续补充证据。",
            "tool_all_failed": "本轮工具调用失败，不能继续补充证据。",
            "final_backtrack_limit": "最终回答前的补充规划次数已达到上限，不能继续补充证据。",
        }
        return f"{stop_reason}：{reason_map.get(stop_reason, '流程已停止，不能继续补充证据。')}"

    def _evidence_text(self, state: AgentGraphState) -> str:
        records = list(state.get("evidence_records") or [])
        cache = dict(state.get("tool_result_cache") or {})
        parts: list[str] = []
        used_keys: set[str] = set()
        index = 1

        for record in records:
            cache_key = str(record.get("cache_key") or "")
            if not cache_key or cache_key in used_keys:
                continue
            content = cache.get(cache_key)
            if content is None:
                continue
            parts.append(self._evidence_part(index, str(record.get("source_tool") or ""), content))
            used_keys.add(cache_key)
            index += 1

        for cache_key, content in cache.items():
            if cache_key in used_keys:
                continue
            parts.append(self._evidence_part(index, "", content))
            index += 1

        return "\n\n".join(parts) if parts else "无"

    def _evidence_part(self, index: int, source_tool: str, content: Any) -> str:
        title, description = self._evidence_title(source_tool)
        return (
            f"{index}. {title}\n"
            f"数据来源说明：{description}\n"
            "证据内容：\n"
            f"{content}"
        )

    def _evidence_title(self, source_tool: str) -> tuple[str, str]:
        mapping = {
            "market_quote": ("行情快照", "当前行情、价格、涨跌幅、成交、换手、量比等。"),
            "market_kline_trend": ("多周期 K 线趋势", "日线、周线、月线趋势、均线、区间位置、波动等。"),
            "market_intraday_summary": ("分时走势摘要", "最近交易日分时路径、冲高回落、跳水、拉升和成交集中度等。"),
            "stock_fundamental_context": ("基本面与估值数据", "财务、分红、PE/PB 历史估值位置等。"),
            "convertible_bond_context": ("可转债数据", "可转债条款、溢价率历史、纯债价值、YTM 和余额变化等。"),
            "watch_pool_context": ("观察池上下文", "当前用户观察池、自选分组、关注标的和备注等。"),
            "scene_report_context": ("历史报告上下文", "已生成报告中的正文、结论和相关上下文。"),
            "scene_signal_context": ("场景信号", "处理器计算出的场景、标签、风险、策略或方法依据。"),
            "knowledge_search": ("知识库参考", "知识库召回片段中的方法解释、风险提示或策略依据。"),
        }
        return mapping.get(source_tool, ("补充证据", "本轮已获取的补充数据或上下文。"))
