# Product

## Register

product

## Users

The product currently serves the creator and family members for personal investment research. It should also be able to expand later to more individual investors who want a focused research workspace rather than a noisy trading portal.

Users are checking watchlists, market movement, configured alerts, knowledge-base context, and AI-generated analysis reports. Their main context is repeated daily review: deciding what deserves attention, what changed, and where to read supporting evidence.

## Product Purpose

This product is a financial research assistant for personal investing. It collects stock, index, and convertible bond market data, manages a private investment knowledge base, and uses AI analysis to produce structured reports with risks, trend judgment, key indicators, and supporting references.

Success means users can open the app and quickly understand which tracked targets need attention, which configured alerts were triggered, what reports or knowledge-base items are ready to review, and where to continue deeper analysis.

## Android 使用场景与体验方向

Android 端面向日常、短时、反复查看：先快速了解市场和当前关注的标的，再根据涨跌、提醒进入搜索或详情，按需维护分组和个人记录。单手操作、清晰的当前上下文和可预测的返回路径是核心体验要求。

已确认的视觉方向是现代、沉稳、清晰的金融工具。先以文字层级、对齐、留白和紧凑的数据布局建立秩序，再用金融蓝突出选择与操作，用涨跌和风险的语义色增加辨识度。页面需要有色彩层次，但不能变成功能按钮平铺、连续卡片堆叠或装饰性彩色仪表盘。

## 行情设计试点与产品边界

- 行情列表与标的详情是已确认的 Android 设计试点。后续将同一视觉语言逐页推广至搜索、分组管理、新增标的和标的设置；不将这些页面的主体改版视为已经完成。
- 保留系统“全部”和用户自选池的区别；类型筛选继续包含全部、A 股、指数和可转债。分组、自选、排序、搜索、详情、新增、编辑、删除和返回路径均是完整产品能力。
- 加载、无数据、筛选无结果、同步失败、部分数据失败、提醒触发和 Snackbar 反馈必须能够区分，不能为了简洁省略状态。
- UI 试点不改变接口、数据来源、ViewModel 状态含义、默认值、提醒阈值和枚举，也不补画现有数据未提供的行情图表。
- 工作台经用户确认采用顶部横向工具入口、自选概况、紧凑报告记录的结构。概况从所有自选分组按标的类型与代码去重，展示上涨、下跌、平盘与暂无行情数量，以及领涨、领跌各前三，帮助用户快速判断自己关注的标的表现；不再重复展示原来的三条关注对比图。汇总独立于既有重点预览状态，不改变其预警优先逻辑，也不将请求失败当作零计数。四个研究工具、报告和工具的当前可用范围、管理员限制均保留。
- 工作台、行情和我的共用一致的底部导航；本轮工作台与共享底栏采用官方 Phosphor 图标资源，不使用 Material Icons 或手画图标。
- 全局 `FinanceTheme` 与本轮范围外的模块保持原样；工作台使用 Material 3，Miuix 仅作为行情模块局部的组件和交互基座。具体视觉规则及平台边界见 [DESIGN.md](DESIGN.md)。

## Brand Personality

Professional, sharp, research-oriented, and technically capable.

The interface should feel like a serious investment research tool: calm enough for repeated use, information-dense where needed, and precise in how it separates market signals, system status, and AI analysis.

## Anti-references

Do not resemble a traditional backend administration template with generic dashboard cards and low-context metrics.

Do not resemble noisy retail trading portals such as Eastmoney-style pages with dense ads, saturated market colors everywhere, visual clutter, and competing attention zones.

Do not resemble a marketing-oriented AI SaaS page with oversized hero content, decorative gradients, vague AI copy, or feature-showcase layout patterns.

## Design Principles

1. Lead with investment context, not system metrics. The first screen should answer what needs attention before showing operational status.
2. Keep signal hierarchy explicit. Watchlist movement, configured alerts, report state, and system monitoring should be visually distinct.
3. Support repeat workflows. The interface should be efficient for daily scanning, comparison, and drilling into details.
4. Use AI as evidence support, not automation theater. AI reports should be easy to review and inspect, but the product should not imply that it makes investment decisions for the user.
5. Prefer restrained precision over decoration. Use color, motion, and density to clarify state and priority, not to imitate trading-terminal excitement.

## Accessibility & Inclusion

Target WCAG AA-level fundamentals for product UI: sufficient text contrast, keyboard-reachable controls, clear focus states, readable table and chart alternatives, and reduced-motion behavior for nonessential transitions.

Charts and status-heavy panels should provide text labels or summaries so key information is not conveyed by color alone.
