from __future__ import annotations

from typing import Any, Callable

import pandas as pd

try:
    import akshare as ak
except ModuleNotFoundError as exc:
    raise SystemExit("未安装 akshare，请先执行: pip install akshare -U") from exc


DEFAULT_SYMBOL = "002958"


def print_df(name: str, df: pd.DataFrame, rows: int = 5) -> None:
    print(f"\n===== {name} =====")
    print(f"rows={len(df)}")
    print(f"columns={list(df.columns)}")
    print(df.head(rows).to_string(index=False))


def call_first_ok(name: str, calls: list[tuple[Callable[..., Any], dict[str, Any]]]) -> pd.DataFrame | None:
    last_error: Exception | None = None
    for func, kwargs in calls:
        try:
            result = func(**kwargs)
            if isinstance(result, pd.DataFrame):
                print_df(name, result)
                return result
            print(f"\n===== {name} =====")
            print(result)
            return None
        except Exception as exc:
            last_error = exc
    print(f"\n===== {name} 调用失败 =====")
    if last_error is not None:
        print(f"{type(last_error).__name__}: {last_error}")
    return None


def fetch_stock_news(symbol: str = DEFAULT_SYMBOL) -> pd.DataFrame | None:
    """个股新闻：用于后续计算 sentiment_positive / sentiment_negative。"""
    return call_first_ok(
        "stock_news_em 个股新闻",
        [
            (ak.stock_news_em, {"symbol": symbol}),
        ],
    )


def fetch_hot_rank_top() -> pd.DataFrame | None:
    """东方财富人气榜 Top：用于判断标的是否处于市场关注名单。"""
    return call_first_ok(
        "stock_hot_rank_em 人气榜",
        [
            (ak.stock_hot_rank_em, {}),
        ],
    )


def fetch_hot_rank_latest(symbol: str = DEFAULT_SYMBOL) -> pd.DataFrame | None:
    """最新人气排名：用于 market_attention_rise。不同版本签名可能不同。"""
    func = getattr(ak, "stock_hot_rank_latest_em", None)
    if func is None:
        print("\n===== stock_hot_rank_latest_em 不存在，跳过 =====")
        return None
    return call_first_ok(
        "stock_hot_rank_latest_em 最新人气排名",
        [
            (func, {"symbol": symbol}),
            (func, {}),
        ],
    )


def fetch_hot_rank_detail(symbol: str = DEFAULT_SYMBOL) -> pd.DataFrame | None:
    """人气历史趋势：用于计算当前关注度相对历史是否升高。"""
    func = getattr(ak, "stock_hot_rank_detail_em", None)
    if func is None:
        print("\n===== stock_hot_rank_detail_em 不存在，跳过 =====")
        return None
    return call_first_ok(
        "stock_hot_rank_detail_em 人气历史趋势",
        [
            (func, {"symbol": symbol}),
        ],
    )


def fetch_hot_rank_realtime(symbol: str = DEFAULT_SYMBOL) -> pd.DataFrame | None:
    """人气实时变动：用于短线关注度变化。不同版本签名可能不同。"""
    func = getattr(ak, "stock_hot_rank_detail_realtime_em", None)
    if func is None:
        print("\n===== stock_hot_rank_detail_realtime_em 不存在，跳过 =====")
        return None
    return call_first_ok(
        "stock_hot_rank_detail_realtime_em 人气实时变动",
        [
            (func, {"symbol": symbol}),
            (func, {}),
        ],
    )


def fetch_hot_keywords(symbol: str = DEFAULT_SYMBOL) -> pd.DataFrame | None:
    """个股热词：后续可辅助 news_driven / policy_driven / herding_effect。"""
    func = getattr(ak, "stock_hot_keyword_em", None)
    if func is None:
        print("\n===== stock_hot_keyword_em 不存在，跳过 =====")
        return None
    return call_first_ok(
        "stock_hot_keyword_em 个股热词",
        [
            (func, {"symbol": symbol}),
        ],
    )


def fetch_baidu_hot_search() -> pd.DataFrame | None:
    """百度股市通热搜：用于全市场热搜关注度对照。"""
    func = getattr(ak, "stock_hot_search_baidu", None)
    if func is None:
        print("\n===== stock_hot_search_baidu 不存在，跳过 =====")
        return None
    return call_first_ok(
        "stock_hot_search_baidu 百度热搜股票",
        [
            (func, {}),
        ],
    )


def main() -> None:
    symbol = DEFAULT_SYMBOL
    print(f"akshare version: {getattr(ak, '__version__', 'unknown')}")
    print(f"test symbol: {symbol}")

    fetch_stock_news(symbol)
    fetch_hot_rank_top()
    fetch_hot_rank_latest(symbol)
    fetch_hot_rank_detail(symbol)
    fetch_hot_rank_realtime(symbol)
    fetch_hot_keywords(symbol)
    fetch_baidu_hot_search()


if __name__ == "__main__":
    main()
