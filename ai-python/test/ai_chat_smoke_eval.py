#!/usr/bin/env python3
"""AI Chat 真实链路手动评测脚本。

脚本使用标准库完成登录、WebSocket 对话和 Token 用量回读，避免为一次手动评测引入额外依赖。
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import secrets
import socket
import ssl
import struct
import sys
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from urllib import error, parse, request


DEFAULT_CASES = (
    {"name": "L1 泛看", "message": "帮我看看宋城演艺"},
    {"name": "L2 指定维度", "message": "宋城演艺趋势和估值怎么看"},
    {"name": "L3 买卖建议", "message": "看看海康威视，给我买卖建议"},
    {"name": "上下文追问", "message": "那我现在能不能买呢"},
)

WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


@dataclass(frozen=True)
class EvalCase:
    name: str
    message: str


class WebSocketError(RuntimeError):
    pass


class SimpleWebSocket:
    def __init__(self, url: str, timeout_seconds: float) -> None:
        self.url = url
        self.timeout_seconds = timeout_seconds
        self.sock: socket.socket | ssl.SSLSocket | None = None

    def connect(self) -> None:
        parsed = parse.urlparse(self.url)
        if parsed.scheme not in {"ws", "wss"}:
            raise WebSocketError(f"不支持的 WebSocket 协议: {parsed.scheme}")

        host = parsed.hostname
        if not host:
            raise WebSocketError("WebSocket URL 缺少 host")
        port = parsed.port or (443 if parsed.scheme == "wss" else 80)
        path = parsed.path or "/"
        if parsed.query:
            path = f"{path}?{parsed.query}"

        raw_sock = socket.create_connection((host, port), timeout=self.timeout_seconds)
        raw_sock.settimeout(self.timeout_seconds)
        if parsed.scheme == "wss":
            self.sock = ssl.create_default_context().wrap_socket(raw_sock, server_hostname=host)
        else:
            self.sock = raw_sock

        key = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
        host_header = host if parsed.port is None else f"{host}:{port}"
        request_text = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host_header}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        self._send_raw(request_text.encode("ascii"))
        response = self._read_http_header()
        status_line, headers = self._parse_handshake_response(response)
        if " 101 " not in status_line:
            raise WebSocketError(f"WebSocket 握手失败: {status_line}")
        expected_accept = base64.b64encode(
            hashlib.sha1(f"{key}{WEBSOCKET_GUID}".encode("ascii")).digest()
        ).decode("ascii")
        actual_accept = headers.get("sec-websocket-accept")
        if actual_accept != expected_accept:
            raise WebSocketError("WebSocket 握手校验失败")

    def send_text(self, text: str) -> None:
        self._send_frame(0x1, text.encode("utf-8"))

    def recv_text(self) -> str:
        fragments: list[bytes] = []
        while True:
            fin, opcode, payload = self._read_frame()
            if opcode == 0x8:
                raise WebSocketError("服务端关闭了 WebSocket 连接")
            if opcode == 0x9:
                self._send_frame(0xA, payload)
                continue
            if opcode == 0xA:
                continue
            if opcode in {0x1, 0x0}:
                fragments.append(payload)
                if fin:
                    return b"".join(fragments).decode("utf-8")
                continue
            raise WebSocketError(f"不支持的 WebSocket frame opcode: {opcode}")

    def close(self) -> None:
        try:
            if self.sock:
                self._send_frame(0x8, b"")
        except Exception:
            pass
        finally:
            if self.sock:
                self.sock.close()
                self.sock = None

    def _send_frame(self, opcode: int, payload: bytes) -> None:
        first = 0x80 | opcode
        length = len(payload)
        if length < 126:
            header = struct.pack("!BB", first, 0x80 | length)
        elif length < 65536:
            header = struct.pack("!BBH", first, 0x80 | 126, length)
        else:
            header = struct.pack("!BBQ", first, 0x80 | 127, length)
        mask = secrets.token_bytes(4)
        masked = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
        self._send_raw(header + mask + masked)

    def _read_frame(self) -> tuple[bool, int, bytes]:
        first, second = self._read_exact(2)
        fin = bool(first & 0x80)
        opcode = first & 0x0F
        masked = bool(second & 0x80)
        length = second & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._read_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._read_exact(8))[0]

        mask = self._read_exact(4) if masked else b""
        payload = self._read_exact(length) if length else b""
        if masked:
            payload = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
        return fin, opcode, payload

    def _read_exact(self, size: int) -> bytes:
        chunks = bytearray()
        while len(chunks) < size:
            if not self.sock:
                raise WebSocketError("WebSocket 未连接")
            chunk = self.sock.recv(size - len(chunks))
            if not chunk:
                raise WebSocketError("WebSocket 连接已断开")
            chunks.extend(chunk)
        return bytes(chunks)

    def _send_raw(self, payload: bytes) -> None:
        if not self.sock:
            raise WebSocketError("WebSocket 未连接")
        self.sock.sendall(payload)

    def _read_http_header(self) -> bytes:
        data = bytearray()
        while b"\r\n\r\n" not in data:
            if not self.sock:
                raise WebSocketError("WebSocket 未连接")
            chunk = self.sock.recv(4096)
            if not chunk:
                raise WebSocketError("WebSocket 握手响应为空")
            data.extend(chunk)
        return bytes(data.split(b"\r\n\r\n", 1)[0])

    @staticmethod
    def _parse_handshake_response(payload: bytes) -> tuple[str, dict[str, str]]:
        lines = payload.decode("iso-8859-1").split("\r\n")
        status_line = lines[0]
        headers: dict[str, str] = {}
        for line in lines[1:]:
            if ":" not in line:
                continue
            key, value = line.split(":", 1)
            headers[key.strip().lower()] = value.strip()
        return status_line, headers


def main() -> int:
    args = parse_args()
    cases = load_cases(args)
    output_path = Path(args.output or default_output_path())
    output_path.parent.mkdir(parents=True, exist_ok=True)

    run_started_at = datetime.now() - timedelta(seconds=2)
    access_token = login(args.base_url, args.username, args.password, args.role_code)
    ws = SimpleWebSocket(build_ws_url(args.base_url, access_token), args.timeout_seconds)

    report: dict[str, Any] = {
        "baseUrl": args.base_url,
        "username": args.username,
        "budgetYuan": args.budget_yuan,
        "startedAt": format_dt(run_started_at),
        "cases": [],
    }

    print(f"已登录 {args.username}，开始连接 AI Chat WebSocket。", flush=True)
    ws.connect()
    print(f"WebSocket 已连接，预算上限 {args.budget_yuan:.4f} 元。", flush=True)

    try:
        previous_run_overview = query_usage(args.base_url, access_token, args.username, run_started_at).get(
            "overview", {})
        for index, case in enumerate(cases, start=1):
            current_usage = query_usage(args.base_url, access_token, args.username, run_started_at)
            current_cost = extract_total_cost(current_usage.get("overview"))
            if current_cost >= args.budget_yuan:
                print(f"预算已达到 {current_cost:.6f} 元，停止发送后续问题。", flush=True)
                break

            print(f"\n[{index}/{len(cases)}] {case.name}: {case.message}", flush=True)
            case_started_at = datetime.now() - timedelta(seconds=1)
            result = run_case(ws, case, args.timeout_seconds)
            time.sleep(args.usage_lag_seconds)
            usage = query_usage(args.base_url, access_token, args.username, case_started_at)
            run_usage = query_usage(args.base_url, access_token, args.username, run_started_at)
            run_overview = run_usage.get("overview", {})
            increment_usage = diff_overview(previous_run_overview, run_overview)
            previous_run_overview = run_overview

            case_record = {
                **result,
                "usage": usage,
                "incrementUsage": increment_usage,
                "runUsageAfterCase": run_usage,
            }
            report["cases"].append(case_record)
            write_report(output_path, report)
            if args.split_output_dir:
                write_case_report(Path(args.split_output_dir), index, case_record)

            case_cost = extract_total_cost(increment_usage)
            run_cost = extract_total_cost(run_usage.get("overview"))
            print_case_summary(case_record, case_cost, run_cost)
    finally:
        report["finishedAt"] = format_dt(datetime.now())
        report["runUsage"] = query_usage(args.base_url, access_token, args.username, run_started_at)
        write_report(output_path, report)
        ws.close()

    final_cost = extract_total_cost(report["runUsage"].get("overview"))
    final_tokens = int(report["runUsage"].get("overview", {}).get("totalTokens") or 0)
    print(f"\n报告已写入: {output_path}", flush=True)
    print(f"本次累计: {final_cost:.6f} 元，{final_tokens:,} token。", flush=True)
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="真实调用 AI Chat 的手动效果评测脚本")
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--role-code", default="admin")
    parser.add_argument("--budget-yuan", type=float, default=0.5)
    parser.add_argument("--timeout-seconds", type=float, default=180.0)
    parser.add_argument("--usage-lag-seconds", type=float, default=1.0)
    parser.add_argument("--case", action="append", default=[], help="追加单条问题；传了以后会替代默认 case")
    parser.add_argument("--cases-file", help="JSON 文件，支持字符串数组或 {name,message} 数组")
    parser.add_argument("--output", help="报告输出路径，默认写入 tmp/ai-chat-eval-*.json")
    parser.add_argument("--split-output-dir", help="可选；每个 case 额外单独写入一个 JSON 文件")
    return parser.parse_args()


def load_cases(args: argparse.Namespace) -> list[EvalCase]:
    if args.cases_file:
        payload = json.loads(Path(args.cases_file).read_text(encoding="utf-8"))
        return [normalize_case(item, index) for index, item in enumerate(payload, start=1)]
    if args.case:
        return [EvalCase(name=f"case-{index}", message=message) for index, message in enumerate(args.case, start=1)]
    return [EvalCase(name=item["name"], message=item["message"]) for item in DEFAULT_CASES]


def normalize_case(item: Any, index: int) -> EvalCase:
    if isinstance(item, str):
        return EvalCase(name=f"case-{index}", message=item)
    if isinstance(item, dict) and item.get("message"):
        return EvalCase(name=str(item.get("name") or f"case-{index}"), message=str(item["message"]))
    raise ValueError(f"无效 case: {item!r}")


def run_case(ws: SimpleWebSocket, case: EvalCase, timeout_seconds: float) -> dict[str, Any]:
    started = time.monotonic()
    message_id = f"msg-{int(time.time() * 1000)}-{uuid.uuid4().hex[:8]}"
    ws.send_text(json.dumps({
        "content": case.message,
        "messageId": message_id,
        "type": "user_message",
    }, ensure_ascii=False))

    progress_events: list[dict[str, Any]] = []
    delta_parts: list[str] = []
    first_delta_seconds: float | None = None

    while True:
        if time.monotonic() - started > timeout_seconds:
            raise TimeoutError(f"等待 final_answer 超时: {case.name}")

        payload = json.loads(ws.recv_text())
        if payload.get("messageId") != message_id:
            continue

        elapsed = round(time.monotonic() - started, 3)
        message_type = payload.get("type")
        content = str(payload.get("content") or "")
        if message_type == "agent_progress":
            progress_events.append({
                "elapsedSeconds": elapsed,
                "status": payload.get("status"),
                "content": content,
            })
            continue
        if message_type == "answer_delta":
            if content and first_delta_seconds is None:
                first_delta_seconds = elapsed
            delta_parts.append(content)
            continue
        if message_type == "final_answer":
            final_answer = content or "".join(delta_parts)
            return {
                "name": case.name,
                "message": case.message,
                "messageId": message_id,
                "conversationId": payload.get("conversationId"),
                "answer": final_answer,
                "answerPreview": compact(final_answer, 260),
                "answerChars": len(final_answer),
                "finalSeconds": elapsed,
                "firstDeltaSeconds": first_delta_seconds,
                "deltaCount": len(delta_parts),
                "deltaChars": sum(len(part) for part in delta_parts),
                "progressCount": len(progress_events),
                "progressEvents": progress_events,
                "flags": detect_flags(final_answer, delta_parts, progress_events),
                "answeredAt": payload.get("answeredAt"),
            }


def detect_flags(answer: str, delta_parts: list[str], progress_events: list[dict[str, Any]]) -> dict[str, bool]:
    all_text = "\n".join([answer, *delta_parts, *(str(item.get("content") or "") for item in progress_events)])
    return {
        "hasDsml": "DSML" in all_text or "tool_calls" in all_text,
        "hasFinalAnswer": bool(answer.strip()),
        "hasStreamingDelta": bool(delta_parts),
    }


def login(base_url: str, username: str, password: str, role_code: str) -> str:
    payload = http_json(
        "POST",
        join_url(base_url, "/api/auth/login"),
        body={"username": username, "password": password, "roleCode": role_code},
    )
    data = unwrap_response(payload)
    token = data.get("accessToken") if isinstance(data, dict) else None
    if not token:
        raise RuntimeError(f"登录成功但未返回 accessToken: {payload}")
    return str(token)


def query_usage(base_url: str, access_token: str, username: str, start_time: datetime) -> dict[str, Any]:
    end_time = datetime.now() + timedelta(seconds=2)
    params = {
        "startTime": format_dt(start_time),
        "endTime": format_dt(end_time),
        "source": "agent",
        "username": username,
    }
    headers = {"Authorization": f"Bearer {access_token}"}
    overview = unwrap_response(http_json(
        "GET",
        join_url(base_url, "/api/ai/token-usage/overview"),
        headers=headers,
        params=params,
    ))
    logs = unwrap_response(http_json(
        "GET",
        join_url(base_url, "/api/ai/token-usage/logs"),
        headers=headers,
        params={**params, "pageNum": 1, "pageSize": 200},
    ))
    return {
        "overview": overview if isinstance(overview, dict) else {},
        "logs": logs if isinstance(logs, dict) else {},
        "phaseSummary": summarize_phases(logs.get("records", []) if isinstance(logs, dict) else []),
    }


def summarize_phases(records: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    summary: dict[str, dict[str, Any]] = {}
    for record in records:
        phase = str(record.get("phase") or "unknown")
        bucket = summary.setdefault(phase, {
            "requestCount": 0,
            "totalTokens": 0,
            "costYuan": 0.0,
        })
        bucket["requestCount"] += 1
        bucket["totalTokens"] += int(record.get("totalTokens") or 0)
        bucket["costYuan"] += extract_total_cost(record)
    return summary


def http_json(
        method: str,
        url: str,
        headers: dict[str, str] | None = None,
        body: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None) -> Any:
    request_url = url
    if params:
        request_url = f"{url}?{parse.urlencode(params)}"
    request_headers = {
        "Accept": "application/json",
        **(headers or {}),
    }
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    req = request.Request(request_url, data=data, headers=request_headers, method=method)
    try:
        with request.urlopen(req, timeout=30) as response:
            response_body = response.read().decode("utf-8")
    except error.HTTPError as exc:
        response_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} {request_url}: {response_body}") from exc
    return json.loads(response_body) if response_body else {}


def build_ws_url(base_url: str, access_token: str) -> str:
    parsed = parse.urlparse(base_url)
    scheme = "wss" if parsed.scheme == "https" else "ws"
    netloc = parsed.netloc
    base_path = parsed.path.rstrip("/")
    query = parse.urlencode({"accessToken": access_token})
    return parse.urlunparse((scheme, netloc, f"{base_path}/ws/ai-chat", "", query, ""))


def join_url(base_url: str, path: str) -> str:
    return f"{base_url.rstrip('/')}/{path.lstrip('/')}"


def unwrap_response(payload: Any) -> Any:
    if isinstance(payload, dict) and "data" in payload and ("code" in payload or "message" in payload):
        return payload.get("data")
    return payload


def extract_total_cost(payload: Any) -> float:
    if not isinstance(payload, dict):
        return 0.0
    estimated_cost = payload.get("estimatedCost")
    if isinstance(estimated_cost, dict):
        return float(estimated_cost.get("totalCost") or 0.0)
    if estimated_cost is not None:
        return float(estimated_cost or 0.0)
    return float(payload.get("costYuan") or 0.0)


def diff_overview(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    numeric_fields = (
        "requestCount",
        "promptTokens",
        "completionTokens",
        "totalTokens",
        "cachedTokens",
        "reasoningTokens",
    )
    result: dict[str, Any] = {}
    for field in numeric_fields:
        result[field] = max(0, int(after.get(field) or 0) - int(before.get(field) or 0))

    before_cost = before.get("estimatedCost") if isinstance(before, dict) else None
    after_cost = after.get("estimatedCost") if isinstance(after, dict) else None
    if isinstance(after_cost, dict):
        result["estimatedCost"] = {}
        for field in ("cacheHitInputCost", "cacheMissInputCost", "outputCost", "totalCost"):
            result["estimatedCost"][field] = max(
                0.0,
                float(after_cost.get(field) or 0.0)
                - float(before_cost.get(field) or 0.0 if isinstance(before_cost, dict) else 0.0),
            )
        result["estimatedCost"]["currency"] = after_cost.get("currency")
    return result


def compact(text: str, max_chars: int) -> str:
    normalized = " ".join(text.split())
    if len(normalized) <= max_chars:
        return normalized
    return f"{normalized[:max_chars]}..."


def format_dt(value: datetime) -> str:
    return value.replace(microsecond=0).isoformat()


def default_output_path() -> str:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return f"tmp/ai-chat-eval-{timestamp}.json"


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


def write_case_report(output_dir: Path, index: int, case_record: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f"{index:02d}-{safe_filename(str(case_record.get('name') or f'case-{index}'))}.json"
    output_path.write_text(json.dumps(case_record, ensure_ascii=False, indent=2), encoding="utf-8")


def safe_filename(value: str) -> str:
    filename = "".join(char if char.isalnum() or char in {"-", "_"} else "-" for char in value.strip())
    return filename.strip("-") or "case"


def print_case_summary(case_record: dict[str, Any], case_cost: float, run_cost: float) -> None:
    overview = case_record.get("incrementUsage") or case_record.get("usage", {}).get("overview", {})
    total_tokens = int(overview.get("totalTokens") or 0)
    flags = case_record.get("flags", {})
    print(
        "结果: "
        f"{case_record.get('answerChars', 0)} 字, "
        f"delta={case_record.get('deltaCount', 0)}, "
        f"首 delta={case_record.get('firstDeltaSeconds')}, "
        f"完成={case_record.get('finalSeconds')}s, "
        f"本轮={case_cost:.6f} 元/{total_tokens:,} token, "
        f"累计={run_cost:.6f} 元, "
        f"DSML={flags.get('hasDsml')}",
        flush=True,
    )
    print(f"预览: {case_record.get('answerPreview')}", flush=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\n已中断。", file=sys.stderr)
        raise SystemExit(130)
