---
name: "Financial Management AI"
description: "金融研究工具的分平台设计规范；下方机器可读 token 为既有 Web 快照，Android 以正文规则及 FinanceTheme 为准。"
# 既有 Web token 快照。px、CSS 字体栈及以下组件尺寸不用于 Android 原生界面。
# Android 的权威 token 来自 FinanceTheme.kt 与行情局部 MarketMiuixTheme.kt。
colors:
  primary-blue: "#006be6"
  dark-surface: "#1c1e23"
  dark-surface-deep: "#14161a"
  dark-foreground: "#f2f2f2"
  dark-border: "#36363a"
  dark-accent: "#2e3033"
  success-green: "#57d188"
  warning-amber: "#efbd48"
  destructive-red: "#dc4446"
  light-surface: "#ffffff"
  light-surface-deep: "#f1f3f6"
  light-foreground: "#333639"
typography:
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: 1.2
  heading:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.3
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary-blue}"
    textColor: "{colors.dark-foreground}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    height: "36px"
  card-panel:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-foreground}"
    rounded: "{rounded.md}"
    padding: "16px 18px"
  status-chip:
    backgroundColor: "{colors.dark-accent}"
    textColor: "{colors.dark-foreground}"
    rounded: "{rounded.sm}"
    padding: "4px 8px"
---

# Design System: Financial Management AI

## Overview

**Creative North Star: "研究驾驶舱"**

共同方向是聚焦、精确、适合反复查看的金融研究工具。设计服务于扫描行情、发现提醒、比较数据和进入详情；Android 与 Web 共享产品定位，但使用各自的平台组件和空间尺度。

### 平台范围与当前设计基线

- **Android（2026-09-05 用户确认）**：沿用本次行情列表与详情的排版，增加有业务含义的色彩层次。内容画布默认平面，分组为文字导航，概览紧凑，列表对齐，主次操作明确；颜色主要表达选择、操作、涨跌和风险。
- **实现范围**：行情列表、详情及共享顶部栏已经落实上述结构；行情局部配色已接入选择控件、报价标签和预警。工作台经用户确认推广同一视觉语言，重排关注、报告及工具入口。搜索、分组管理、新增标的、标的设置的页面主体仍按后文顺序逐页推广，不能认为整个模块或全 App 已完成改版。
- **主题边界**：全局 [FinanceTheme.kt](android-app/app/src/main/kotlin/com/scrapider/finance/androidapp/designsystem/FinanceTheme.kt) 是不变的 Android 基础 token 来源；[FinanceSignalColors.kt](android-app/app/src/main/kotlin/com/scrapider/finance/androidapp/designsystem/FinanceSignalColors.kt) 从现有 token 派生行情与工作台共用的信号色，不安装新主题。[MarketMiuixTheme.kt](android-app/app/src/main/kotlin/com/scrapider/finance/androidapp/feature/market/theme/MarketMiuixTheme.kt) 只负责行情局部映射。Miuix 0.9.3 不向其他模块扩散，工作台继续使用 Material 3。
- **Web**：既有实现使用 Vben Admin、Element Plus、ECharts、CSS token 和深色工作面。本文前置 YAML 及明确标为 Web 的规则保留该平台的设计参考，不是 Android 的布局模板。
- **设计工具预览**：[.impeccable/design.json](.impeccable/design.json) 中既有 HTML／CSS 组件、动效与断点仅用于 Web 预览，不能当作 Android 渲染或原生 token 来源。
- **旧方案的效力**：[STITCH_APP_DESIGN.md](docs/STITCH_APP_DESIGN.md) 等早期草案中的五项底部导航、红涨绿跌及 Web 像素尺寸，不能覆盖当前 Android 的三项导航、现有涨跌语义和本次确认方案。功能对照文档不代表所有设想已在 Android 实现。

用户已确认设计方向与本次排版；Kotlin 编译和新增配色的计算对比度已检查。实际深色、大字号和不同设备尺寸下的视觉验收仍需真实 Android 截图，不能把代码检查当作设备验收。

### Web 既有视觉特征

- Dark, restrained working surface with blue used for current selection and primary action.
- Dense but organized market data, alert state, report state, and knowledge context.
- Flat or lightly layered panels, 6-8px practical radii, and consistent Element Plus control vocabulary.
- State-first color: red, green, and amber communicate market or workflow state, not decoration.

## Colors

### Android：选择、操作与信号色

页面以白色／深色中性画布和清晰正文承载数据，金融蓝负责当前选择与主要操作。增加色彩时优先强化这些角色，不给不同分组、类型或区块随意分配装饰色。

以下是当前原生主题的关键映射；代码中的 token 是维护来源，不在页面内重新写死这些数值。

| 角色 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `primary` / `onPrimary` | `#006BE6` / `#FFFFFF` | `#A9C7FF` / `#00315F` | 选中分组文字和指示线、添加等主要操作 |
| `primaryContainer` / `onPrimaryContainer` | `#E8F1FF` / `#003E8E` | `#004A92` / `#D6E3FF` | 当前类型筛选的浅色底和文字 |
| `surface` / `onSurface` | `#FFFFFF` / `#111722` | `#10141B` / `#E8ECF3` | 内容画布、名称、主要价格 |
| `surfaceVariant` | `#F2F5F8` | `#202731` | 中性状态和必要的次级表面 |
| `positive` | `#078A3E` | `#69DB9D` | 正向涨跌：沿用当前 Android 的绿色语义 |
| `negative` | `#C72C2C` | `#FFB4AB` | 负向涨跌：沿用当前 Android 的红色语义 |
| `warning` | `#A66200` | `#FFC56B` | 提醒触发、越界等需要查看的状态 |

- 行情概览的点位按涨跌着色；列表主要价格保持中性。`MarketChangeLabel` 在概览、列表和详情统一表达涨跌幅，保留正负号及格式化数值；零涨跌和暂无数据使用中性色。
- `rememberFinanceSignalColors()` 集中提供涨跌与预警的前景／背景配对，行情通过 `LocalMarketSignalColors` 使用，工作台直接消费：浅色使用 `LIGHT_SIGNAL_TINT_ALPHA`（0.08），深色使用 `DARK_SIGNAL_TINT_ALPHA`（0.14），与当前 `surface` 合成为不透明底色；前景通过 `SIGNAL_FOREGROUND_ALPHA`（0.84）与 `onSurface` 合成。不要在各组件散落透明度或复制计算后的色值。
- 预警只有在既有状态已触发时使用琥珀色容器和文字；不能用颜色制造不存在的风险、成功或刷新状态。
- 正常字号文字对比度目标至少 4.5:1，大字及非文本操作标记至少 3:1。每次调整配色都检查深浅色实际前景／背景组合；颜色之外还要有文字、形状或选中标记。

### Web：既有色板

The palette is a restrained dark product system with one blue command color and semantic market/status colors.

#### Primary
- **Command Blue** (`#006be6`, `--primary: 212 100% 45%`): Primary buttons, selected navigation, active tab emphasis, chart highlights, and links. Use sparingly so it remains an action and selection signal.

#### Secondary
- **Research Surface** (`#1c1e23`, `--background` / `--card` in dark mode): Main app and card surface. It should carry most authenticated screens.
- **Deep Workspace** (`#14161a`, `--background-deep`): Page background and lower visual layers behind panels.
- **Control Accent** (`#2e3033`, `--accent`): Hover states, inactive chips, muted toolbar controls, and low-priority containers.

#### Tertiary
- **Signal Green** (`#57d188`, `--success`): Positive market movement, successful workflow states, and confirmation indicators.
- **Alert Amber** (`#efbd48`, `--warning`): Near-threshold alerts, pending states, and attention-needed status.
- **Risk Red** (`#dc4446`, `--destructive`): Negative market movement, failure states, and destructive actions.

#### Neutral
- **Primary Text** (`#f2f2f2`, `--foreground`): Body text and high-priority data on dark surfaces.
- **Dark Divider** (`#36363a`, `--border`): Panel borders, table separators, input borders, and chart grid lines.
- **Light Surface** (`#ffffff`, `--background` in light mode): Optional light-mode content surface.
- **Light Workspace** (`#f1f3f6`, `--background-deep` in light mode): Optional light-mode page background.
- **Light Text** (`#333639`, `--foreground` in light mode): Primary light-mode body text.

#### Named Rules

**The Signal Rarity Rule.** Blue, red, green, and amber must indicate action, selection, market direction, or workflow state. They should not be used as decorative fills.

**The No Trading-Portal Noise Rule.** Do not create competing saturated zones, blinking-feeling alerts, or dense color-coded clusters that make the page resemble a retail trading portal.

## Typography

### Android

使用平台默认字体和已有主题角色，以字重、字号和位置建立主次；财务数值采用 `tnum` 等宽数字特性，并保留现有精度、千位分隔和暂无数据文案。

| 内容 | 主题角色 | 当前规格 |
| --- | --- | --- |
| 行情标题、详情标的名 | `MiuixTheme.textStyles.title1` → `displaySmall` | 24sp / 32sp，Bold |
| 列表上下文和主要章节 | `title2` → `headlineSmall` | 18sp / 24sp，SemiBold |
| 分组、普通标题和列表价格 | `title3` → `titleMedium` | 16sp / 22sp，Medium；分组按选中状态调整字重 |
| 正文、详情字段 | `body1` → `bodyMedium` | 14sp / 20sp，Normal |
| 代码、计数、提醒和辅助数据 | `body2` → `bodySmall` | 13sp / 18sp，Normal |
| 详情报价主数值 | `MaterialTheme.typography.headlineLarge` | 使用既有 Material 3 角色，不在组件内写死字号 |

长分组名称通过横向滚动完整访问；详情名称允许换行，备注采用上下排列完整展示。不能通过缩小字体或固定行高适配大字号。

### Web：既有字体层级

**Display Font:** System sans stack, no separate display face.  
**Body Font:** System sans stack.  
**Label/Mono Font:** System sans for labels; use tabular numeric features where data alignment matters.

**Character:** The typography should be functional, compact, and confident. Hierarchy comes from weight, spacing, and table structure rather than oversized headings.

#### Hierarchy
- **Display** (700, 28px, 1.2): Page-level domain titles such as an active target name or the Investment Workbench title. Use rarely.
- **Headline** (700, 18-20px, 1.25): Major page sections and cockpit summary headers.
- **Title** (700, 16px, 1.2): Panel headings, table-adjacent sections, and chart titles.
- **Body** (400, 14px, 1.5): General product UI text, descriptions, table cells, and notes.
- **Label** (500, 12-13px, 1.3): Metadata, timestamps, filter labels, secondary values, and compact status text.

#### Named Rules

**The Data Before Drama Rule.** Do not use hero-scale typography inside app screens. Headings should help scanning, not dominate the working surface.

**The Numeric Clarity Rule.** Financial values, percentages, prices, and counts should align cleanly and use consistent precision.

## Layout

### Android：行情列表

常规阅读顺序为：单行顶部栏（行情、搜索）→ 紧凑市场概览 → 分组文字导航与管理 → 当前列表标题、计数及添加 → 类型筛选、排序 → 标的列表。

- 同步异常提示仅在已有状态需要时出现在内容开头；触发预警仅在有对应数据时放于分组之后、列表标题之前。它们不占据正常状态的空白占位。
- 市场概览与当前自选列表属于不同范围，不能插在列表标题和标的之间。沿用现有概览数据上限；空间够用时横向三列展示，窄宽／大字号下转为紧凑纵向排列。
- 每个概览列的适配最小宽度由 `LocalFinanceDimensions.toolRowHeight` 与字体缩放共同决定，依据实际可用宽度排布，不把截图的像素宽度当成固定断点。
- 分组按内容宽度横向滚动，选中项通过蓝色文字、字重和短指示线辨识；“管理”是独立次级操作，不能重新做成与分组同等视觉重量的大按钮。
- 列表标题、标的名称和代码保持共同起点；右侧价格与涨跌幅对齐。类型筛选紧邻被筛选的列表；排序保持可见入口及当前方式的无障碍描述。
- 类型和排序的既有分组显示条件不变；系统“全部”显示系统标的，用户分组显示对应自选，不混合这两种产品含义。

### Android：详情与完整页面流

详情采用平面的名称／代码区域和单次报价展示；最新价与当日涨跌幅集中出现一次，不再额外重复一张摘要卡。自选状态、所在分组、提醒、买入价、持仓和备注按既有条件展示，设置作为明确操作。仅使用已有快照字段，不补画无数据支撑的走势图。

搜索、详情、管理、新增和设置沿用当前入口及返回栈。搜索结果点入详情后返回原搜索；设置与详情返回进入来源；删除保留确认；加载、空态、错误和 Snackbar 反馈均完整保留。

后续推广顺序：搜索 → 分组管理 → 新增标的 → 标的设置。复用已确认的排版、选择色、信号标签和操作层级，并逐页检查表单、Sheet、菜单、保存状态和返回路径；不把本轮确认解释为全 App 主题替换授权。

### Android：工作台

工作台按紧凑标题／问候与刷新 → 横向研究工具 → 自选概况 → 紧凑报告记录排列；同步异常仍在内容开头显示原错误文案与重试。视觉重心是“关注标的中有多少上涨／下跌，哪些涨跌最多”，不再重复行情列表，也不为工具和报告预留大块空白。

- 研究工具默认四入口横排，图标在上、名称在下，不再占用四行列表。根据可用宽度和字体缩放重排为两列或一列；保留完整名称，描述可通过原生长按提示与无障碍访问。非管理员仍看到禁用入口、“仅管理员可用”和锁图标。分配列宽的权重必须由行的直接子布局承接，不能只传给 Tooltip 的内部锚点，避免首项占满整行并挤掉其他入口。
- 自选概况明确标注“全部分组 · 去重后 N 只”，突出上涨／下跌数量，辅以平盘和暂无行情数量。数据来自完整自选分组，按原始标的类型与代码去重；零值归平盘，缺失或非有限涨跌幅归暂无行情。新增独立的汇总状态，保留既有三条重点预览、预警优先逻辑和阈值；仅汇总正负涨跌幅，不虚构历史走势或预警统计。
- 领涨前三按正涨跌幅降序，领跌前三按负涨跌幅升序，保留名称、类型、代码和准确百分比。两侧都有数据且空间足够时使用无外层卡片的双栏排行；单边行情、窄屏或大字号时按实际高度纵向展开，空的一侧只显示简短说明，不保留空半栏。入口沿用前往行情的回调，不暗示已实现直接打开标的详情。
- 自选请求失败不展示零计数；加载、成功无自选、部分或全部行情缺失、同步失败分别表达，原错误与重试路径保留。概况只表达当前获得的快照，不标注未经数据支持的实时性。
- 报告采用紧凑记录行，以小型文档图标、标的名称、类型、完整时间和状态标签形成阅读层级，不使用横向大卡片或固定卡高。已生成用绿色，生成中用蓝色，失败用红色，待处理和未知保持中性；保留最多三份预览与“查看全部”，不虚构正文摘要或生成进度。
- 报告及工具的未实现页面继续走原 Snackbar 回调，不把视觉改版描述为功能补齐。

涨跌数量与排行的每列宽度不足 `LocalFinanceDimensions.toolRowHeight × OVERVIEW_COLUMN_WIDTH_UNITS × fontScale` 时转为单列；长名称、问候、报告时间和描述允许换行，不通过缩字适配。辅助文字使用派生的中性前景色，保持对比度而不修改全局灰色 token。

### Android：共享底部导航与图标

工作台、行情和我的通过 `AppShell` 中同一个 `FinanceBottomNavigation` 渲染底栏，保留各自 Scaffold／返回路径和原生系统栏处理，不把行情 Miuix 扩散到其他模块。底栏显式使用 `surface` 中性画布和细分隔线，选中图标与文字为 `primary`，未选中为派生中性前景；去掉紫色默认底与选中胶囊。选中态使用同一图标的实心版本，未选中态使用线性版本，不能仅靠颜色区分。

本轮工作台及共享底栏使用官方 Phosphor 成品图标，以 VectorDrawable 资源接入，不手画、不引入整库运行时依赖。固定源版本、原始文件和 MIT 许可见 [THIRD_PARTY_NOTICES.md](android-app/THIRD_PARTY_NOTICES.md)，许可也随应用 assets 分发。其他页面的存量图标迁移不自动纳入本轮范围。

### Android：间距与适配

复用 `LocalFinanceSpacing`：`xxs / xs / sm / md / lg / xl / xxl / section` 分别为 `2 / 4 / 8 / 12 / 16 / 20 / 24 / 32dp`。当前行情列表与详情正文左右边距为 `xl`，标准行内纵向间距为 `md`；同组信息紧凑，不同区块保留更大间隔。

触控目标至少为 `LocalFinanceDimensions.minTouchTarget`（48dp）。标题、数量和操作按可用宽度分配或换行；详情报价不足宽度时换行。顶部与底部尊重系统栏、挖孔和导航区域；保留系统返回、TalkBack 标题／选中语义及可打断的组件交互，避免额外入场动画和弹跳。

### Web

Web 继续采用适合桌面的表格、网格与工作区布局，窄宽时结构性收拢；其 CSS 断点、面板尺寸和像素间距不移植为 Android 原生规范。

## Elevation & Depth

### Android

平面内容以排版、对齐和留白建立层级。淡色涨跌标签和预警容器分别表达数据方向与风险，不升级成连续卡片；普通章节和列表行不依赖阴影。Sheet、菜单及需要隔离的表单沿用 Material 3／行情局部 Miuix 语义，不添加玻璃、模糊、发光或装饰性弹跳。

### Web

The system is flat by default and uses tonal separation, borders, and density before shadow. Existing Vben tokens include `--shadow-float`, but investment screens should use it only for overlays, dropdowns, popovers, and temporary floating UI.

#### Shadow Vocabulary
- **Float Shadow** (`0 6px 16px 0 rgb(0 0 0 / 8%), 0 3px 6px -4px rgb(0 0 0 / 12%), 0 9px 28px 8px rgb(0 0 0 / 5%)`): Popovers, menus, and transient overlays.
- **Panel Border** (`1px solid var(--el-border-color-light)` or `1px solid hsl(var(--border))`): Default separation for cards, dashboards, tables, and cockpit panels.

#### Named Rules

**The Flat Working Surface Rule.** Persistent panels should not depend on soft shadows. Use background contrast, border, and layout rhythm first.

## Shapes

Android 复用 `FinanceShapes`：`extraSmall / small / medium / large / extraLarge` 分别为 `4 / 8 / 12 / 16 / 24dp`。涨跌状态标签使用 `extraSmall`；按钮、筛选和浮层遵循各自组件形态，不能用统一大圆角把所有内容包装成按钮或卡片。容器内部必须明确设置内容间距，不能假设 Miuix Card 自动提供内边距。

Web 沿用前置 token 的圆角与下方组件规范，不作为 Android 组件默认值。

## Components

### Android：已确认的组件表达

| 组件 | 视觉规则 | 交互与状态 |
| --- | --- | --- |
| 顶部栏 | 单行标题、清晰的搜索／返回图标，避开系统安全区 | 复用原搜索与返回回调 |
| 分组导航 | 可横滑文字、选中字重与蓝色短下划线，无连续描边按钮 | 系统“全部”与用户组保持原含义，管理是次级入口 |
| 类型筛选 | 选中项使用 `primaryContainer`，未选中项保持轻量 | 单选；空间不足可横滑，排序打开既有 Sheet |
| 添加／设置 | 每页至多一个主操作，以 `primary` / `onPrimary` 突出 | 添加保留对应自选池参数；不新增不支持的操作 |
| 报价标签 | 统一 `MarketChangeLabel`，浅色底配语义前景与正负号 | 零值和缺失值中性，不靠颜色单独表达数据 |
| 预警 | 既有独立容器使用局部琥珀色，保留明确触发文案 | 按原状态和数量展示，点击进入对应详情 |
| 列表与详情字段 | 列表平面排列，详情用清晰键值及完整备注 | 保留更多菜单、删除确认和设置入口 |

搜索和管理表单的进一步设计应沿用这些规则，但这里不把其页面主体描述为已改版。图标仅使用已有合法资源或批准的组件库资源。

### Web：既有组件规则

#### Buttons

- **Shape:** Practical rounded rectangles, usually 8px via `--radius: 0.5rem`; icon buttons may use 4-6px.
- **Primary:** Command Blue background with high-contrast foreground, 36px default height, compact horizontal padding.
- **Hover / Focus:** Slight color shift and visible focus ring. Do not add decorative glow.
- **Secondary / Ghost / Tertiary:** Use muted or transparent backgrounds with clear hover states. Reserve primary buttons for real forward actions.

#### Chips

- **Style:** Compact tags using Element Plus tags or token-based muted backgrounds. Use green/red/amber only for state or market direction.
- **State:** Selected filters should be blue or clearly bordered. Passive labels should stay neutral.

#### Cards / Containers

- **Corner Style:** 6-8px for most business panels; 12px is acceptable for reusable framework cards. Avoid 24px+ radii.
- **Background:** Use `--el-bg-color`, `--card`, or `--el-fill-color-lighter` depending on nesting depth.
- **Shadow Strategy:** Persistent cards are flat with borders. Floating UI may use Float Shadow.
- **Border:** Use token borders, not decorative side stripes.
- **Internal Padding:** 16-24px for page-level panels; 12-14px for dense metric items.

#### Inputs / Fields

- **Style:** Element Plus controls are the baseline. Use dark input backgrounds and token borders.
- **Focus:** Ring or border shift must be visible and consistent.
- **Error / Disabled:** Use framework error and disabled states; never encode state by color alone.

#### Navigation

- **Style:** Sidebar navigation with rounded active items and icon labels. The first Dashboard item should be the primary daily entry when the Investment Workbench ships.
- **Tabbar:** Chrome-style tabs are enabled. Only the default home page should be affixed by default.
- **Mobile:** Collapse dense grids and tables structurally; do not rely on fluid heading sizes.

#### Charts and Tables

- **Charts:** ECharts should use restrained line/bar styles, visible tooltips, and semantic colors. Avoid overloaded legends and decorative gradients.
- **Tables:** Tables are allowed to be dense. Prioritize sticky affordances, readable numeric alignment, and useful empty/loading states.

## Do's and Don'ts

### Android

- 保留已确认的内容顺序和排版节奏；增加色彩时强化选择、操作和业务信号，而不是为每个区块换一种装饰色。
- 复用全局 token 与共享的派生信号色；行情的 Miuix 映射仍保持局部，变更某个模块的视觉不自动扩大为全局主题或其他模块迁移。
- 先保留完整业务、状态和返回路径，再调整容器、密度、图标和文字；不以“精简 UI”为由修改默认参数、阈值、枚举或数据来源。
- 不复制 iOS 导航、Web 后台布局或早期草案的尺寸／颜色约定；不回退到等宽大按钮分组、功能平铺、连续卡片、文字贴边和重复报价。
- 验收覆盖浅色、深色、长分组名、大字号、加载／空／错误态及返回操作。用户截图用于视觉判断，编译和计算对比度用于工程检查，两者不能互相替代。

### Web：Do


- **Do** lead app home screens with investment context: watchlist movement, configured alerts, report state, and evidence links.
- **Do** use `#006be6` / `--primary` for primary action and selection, not broad decoration.
- **Do** keep panel radii in the 6-12px range and use 16px grid gaps for dense dashboard layouts.
- **Do** preserve familiar Element Plus controls unless a replacement improves the workflow clearly.
- **Do** provide loading, empty, error, and disabled states for every workbench module.
- **Do** make charts and market-state panels readable without relying on color alone.

### Web：Don't

- **Don't** resemble a traditional backend administration template with generic dashboard cards and low-context metrics.
- **Don't** resemble noisy retail trading portals such as Eastmoney-style pages with dense ads, saturated market colors everywhere, visual clutter, and competing attention zones.
- **Don't** resemble a marketing-oriented AI SaaS page with oversized hero content, decorative gradients, vague AI copy, or feature-showcase layout patterns.
- **Don't** use colored side-stripe borders, gradient text, glassmorphism, decorative glow, or repeated identical card grids.
- **Don't** imply that AI makes investment decisions for the user. AI reports are reviewable evidence and workflow context.
- **Don't** add page-load choreography. Product screens should load into the task.
