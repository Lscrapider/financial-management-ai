# App 端页面功能设计对照文档

更新时间：2026-06-14

本文用于 App 端页面设计和功能对照，不提供视觉稿。内容依据当前 Web 路由、导航、前端 API 封装和后端 `SecurityConfig` 梳理，目标是让后续 UI 设计和 App 开发能明确每个页面的浏览权限、展示数据、功能点、点击逻辑和接口边界。

## 1. 文档范围

覆盖当前业务路由和全局能力：

- 认证与个人中心：登录、注册、验证码登录占位、二维码登录占位、忘记密码占位、个人中心。
- 主业务页面：投资工作台、股票行情、指数行情、可转债行情、投资观察池、布控提醒。
- 研究页面：标的分析报告、报告工作台、投资心理画像。
- 知识库页面：知识库概览、知识库材料、知识库浏览、OCR 导入、OCR 人工复核、手动知识导入。
- 管理页面：系统监控、Token 用量、标的配置、数据同步。
- 全局能力：AI 研究助手浮窗。

不作为 App 主功能设计对象：

- Web 组件 demo 页面。
- 403、404 等框架兜底页，仅需要 App 端保留无权限和不存在页面的通用状态。

## 2. 全局权限规则

### 2.1 前端页面权限

当前 Web 使用前端权限模式，登录成功后根据 `GET /api/user/info` 返回的用户角色生成可访问路由。

| 权限级别 | 判断方式 | 页面表现 |
| --- | --- | --- |
| 匿名可访问 | 认证相关 core routes | 登录、注册等页面不需要 access token。 |
| 已登录用户 | 有 access token 且路由未配置 `authority` | 工作台、行情、观察池、报告、心理画像、报告工作台、个人中心等。 |
| 管理员 | 路由或父路由配置 `authority: ['admin']` | 系统配置、知识库管理、OCR 导入、手动知识导入等。 |
| 菜单隐藏但可访问 | `hideInMenu: true`，仍受角色控制 | 个人中心、OCR 人工复核等。 |

App 端权限建议：

- 登录态失效统一跳登录页，并保留原目标页用于登录后回跳。
- 菜单和页面入口按 `roles` 控制，`admin` 才展示知识库管理、系统管理和 OCR 管理入口。
- 页面可见性不能替代接口权限，App 调接口仍必须处理 401 和 403。

### 2.2 后端接口权限

后端使用 Spring Security 兜底，接口权限比前端菜单更重要。

| 接口类型 | 权限 |
| --- | --- |
| `/api/auth/login`、`/api/auth/register`、`/api/auth/refresh`、`/api/auth/logout` | 匿名可访问。 |
| `/api/ws/**`、AI callback POST | 匿名可访问，仅供后端异步回调或 WebSocket 握手使用。 |
| `/api/system-config/**` | admin。 |
| `/api/stocks/sync/**`、`/api/indices/sync/**`、`/api/bonds/sync/**` | admin。 |
| `/api/knowledge/**` | admin。 |
| `/api/ai/ocr/**`、`/api/ai/manual-knowledge/**`、`/api/ai/knowledge-material/**` | admin。 |
| `/api/ai/console/**`、`/api/ai/token-usage/**` | admin。 |
| 其他 `/api/**` | 已登录用户。 |

鉴权细节：

- App 请求需要携带 `Authorization: Bearer <accessToken>`。
- access token 失效时可调用 `POST /api/auth/refresh` 刷新；失败后清空登录态。
- 后端会校验 JWT 类型为 `access`、用户存在且启用，并写入 `ROLE_<roleCode>`。
- `hasRole('admin')` 对应用户角色 `admin`。

## 3. App 信息架构对照

当前 Web 导航分组可映射为 App 功能分组：

| 分组 | 页面 | App 端定位 |
| --- | --- | --- |
| 工作台 | 投资工作台 | 登录后默认首页，展示今日信号和核心入口。 |
| 行情 | 股票行情、指数行情、可转债行情 | 市场数据查询和图表页。 |
| 观察风控 | 投资观察池、布控提醒 | 用户自选标的、成本持仓和阈值提醒。 |
| 研究 | 标的分析报告、报告工作台、投资心理画像 | AI 研究、报告和个性化建议基础。 |
| 知识 | 知识库概览、知识库材料、知识库浏览、OCR 导入、手动知识导入 | admin 知识库管理和材料检索。 |
| 系统 | 系统监控、Token 用量、标的配置、数据同步 | admin 运维和基础数据管理。 |
| 我的 | 个人中心、登录退出 | 账号资料、安全设置和通知偏好。 |

## 4. 页面功能设计

### 4.1 登录

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/auth/login` |
| 浏览权限 | 匿名；已有 access token 时跳转 redirect、用户首页或 `/investment-workbench`。 |
| 页面目标 | 账号密码登录。 |
| 展示数据 | 角色选择 `Admin/User`、用户名、密码、滑块验证码、记住账号、忘记密码、注册链接。 |
| 功能点 | 登录校验、保存 token、拉取用户信息和权限、登录后回跳。 |
| 点击逻辑 | 提交表单后调用登录；成功保存 token，拉取用户信息，再进入默认首页；失败展示错误。 |
| 后端接口 | `POST /api/auth/login`、`GET /api/user/info`、`GET /api/auth/codes`。 |
| App 端关注点 | 保留角色选择和登录后 redirect；验证码、人机校验和登录中状态需要明确。 |

### 4.2 注册

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/auth/register` |
| 浏览权限 | 匿名。 |
| 页面目标 | 创建新账号。 |
| 展示数据 | 用户名、密码强度、确认密码、条款和隐私政策确认。 |
| 功能点 | 密码强度提示、两次密码一致校验、协议确认、注册成功回登录。 |
| 点击逻辑 | 表单通过后提交注册；成功提示并跳转登录页。 |
| 后端接口 | `POST /api/auth/register`。 |
| App 端关注点 | 没有勾选协议时禁止提交；注册成功后回登录，不直接进首页。 |

### 4.3 验证码登录、二维码登录、忘记密码

| 页面 | Web 路由 | 当前状态 | App 端处理 |
| --- | --- | --- | --- |
| 验证码登录 | `/auth/code-login` | 前端占位，发送验证码直接抛错，未接真实接口。 | 无短信接口前建议隐藏入口或标为暂不可用。 |
| 二维码登录 | `/auth/qrcode-login` | 静态二维码占位，未接扫码会话。 | 无扫码接口前不建议保留。 |
| 忘记密码 | `/auth/forget-password` | 仅邮箱格式校验，提交逻辑为空。 | 无重置密码接口前建议隐藏或标为暂不可用。 |

### 4.4 个人中心

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/profile` |
| 浏览权限 | 已登录用户；菜单隐藏，通过头像入口进入。 |
| 页面目标 | 维护个人资料、安全联系方式、密码和通知偏好。 |
| 展示数据 | 头像、姓名、用户名、基本设置、安全设置、修改密码、新消息提醒。 |
| 功能点 | 修改姓名和简介；维护邮箱、手机；修改密码；开关邮件通知。 |
| 点击逻辑 | 进入时拉取用户信息；保存资料后同步用户 store；修改密码校验确认密码；通知开关变更即提交。 |
| 后端接口 | `GET /api/user/info`、`PUT /api/user/info`、`PUT /api/user/password`、`PUT /api/user/notification`。 |
| App 端关注点 | 用户名不可编辑；密码修改属于高风险操作，需要清晰反馈；通知开关失败时应提示并回滚状态。 |

### 4.5 AI 研究助手浮窗

| 项 | 内容 |
| --- | --- |
| Web 入口 | BasicLayout 全局浮窗按钮 `AI 研究助手`。 |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 在当前页面上下文中进行投资研究问答。 |
| 展示数据 | 会话消息、模型名、流式回答、窗口尺寸和位置、当前页面上下文入口。 |
| 功能点 | 打开/收起、拖拽、缩放、全屏、发送消息、流式接收回复。 |
| 点击逻辑 | 打开浮窗后可输入问题；发送消息通过 WebSocket 通道流式返回；窗口布局保存在本地。 |
| 后端接口 | `POST /api/ai/chat/ws-ticket`、WebSocket `/ws/ai-chat`。 |
| App 端关注点 | App 端不需要照搬桌面浮窗，可设计为全屏对话或底部入口；必须保留当前用户登录态、会话流式状态、上下文来源说明。 |

### 4.6 投资工作台

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/investment-workbench` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 聚合观察池、布控提醒和报告动态，给出今日优先关注信号。 |
| 展示数据 | 观察池标的总数、分组数、上涨/下跌数量、报告异常数、信号雷达、今日行动、观察池异动、资产分布、布控风险、最新报告。 |
| 功能点 | 聚合多个模块状态；按风险优先级生成今日行动；从卡片跳转到观察池、提醒和报告。 |
| 点击逻辑 | 刷新并发拉取观察池、提醒和报告目标；观察池条目跳转并携带 `groupId/itemId`；报告有 `latestReportId` 时进入报告工作台，否则进入报告列表。 |
| 后端接口 | `GET /api/watch-pool/groups`、`GET /api/stock-alerts`、`GET /api/ai/scene-analysis/tasks/reports/targets?pageNum=1&pageSize=4`。 |
| App 端关注点 | 首页首屏优先展示“今日行动”和风险信号；保留上涨红、下跌绿；卡片需要能跳转到具体标的或配置页。 |

### 4.7 股票行情

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/workspace` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 查看股票行情列表、盘口指标、走势和相关指数快照。 |
| 展示数据 | 指数快照、股票列表、当前股票、最新价、涨跌幅、成交量、均价、盘口详情、分时图、K 线图、更多行情。 |
| 功能点 | 市场筛选、表格排序、选中股票、图表周期切换、复权类型切换、指数卡片跳转。 |
| 点击逻辑 | 切市场或排序重新拉取股票列表；点击股票刷新详情和图表；点击指数卡片跳指数行情并携带 `secid`；更多打开行情详情。 |
| 后端接口 | `GET /api/indices/quotes`、`GET /api/stocks/quotes`、`GET /api/stocks/intraday-trends`、`GET /api/stocks/klines`。 |
| App 端关注点 | 保留“列表选标的 + 详情图表”的主从关系；更多行情适合做详情页或底部抽屉；K 线模式下才显示复权选项。 |

### 4.8 指数行情

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/index-market` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 查看指数快照、核心指标、分时和 K 线走势。 |
| 展示数据 | 指数名称、代码、交易所、更新时间、点位、涨跌幅、成交量、成交额、今开、昨收、最高、最低、分时/K 线。 |
| 功能点 | 支持 `query.secid` 预选指数；指数列表排序；周期切换；K 线范围选择。 |
| 点击逻辑 | 初始化优先选中 `secid`；行点击切换指数；刷新列表并更新图表；周期/范围变化重新请求图表。 |
| 后端接口 | `GET /api/indices/quotes`、`GET /api/indices/intraday-trends`、`GET /api/indices/klines`。 |
| App 端关注点 | 保留从股票页指数卡片跳转定位能力；指数页信息可比股票页更聚焦点位和图表。 |

### 4.9 可转债行情

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/bond-market` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 查看可转债行情、评级、转股溢价率和走势。 |
| 展示数据 | 转债名称、代码、交易所、最新价、涨跌幅、评级、转股溢价率、成交量、成交额、盘口指标、分时/K 线。 |
| 功能点 | 支持 `query.secid` 预选；转债列表排序；周期切换；K 线范围选择；更多行情查看。 |
| 点击逻辑 | 初始化优先选中 `secid`；行点击切换转债；刷新列表和图表；更多打开行情明细。 |
| 后端接口 | `GET /api/bonds/quotes`、`GET /api/bonds/intraday-trends`、`GET /api/bonds/klines`。 |
| App 端关注点 | 评级和转股溢价率是转债页关键字段，不能省略；图表交互可与指数页保持一致。 |

### 4.10 投资观察池

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/watch-pool` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 维护观察标的、分组、成本、持仓和备注，并叠加最新行情。 |
| 展示数据 | 分组列表、当前分组标的、类型筛选、最新价、涨跌幅、成交额、买入价、持仓数量、持仓市值、浮盈、盈亏比例、备注、更多行情。 |
| 功能点 | 新建/重命名/删除分组；添加标的；编辑成本和备注；移除标的；刷新行情；从工作台跳转后定位高亮。 |
| 点击逻辑 | 分组切换刷新当前表；添加标的支持按类型搜索和多选；编辑保存成本/持仓/备注；删除二次确认；更多查看股票或转债详情。 |
| 后端接口 | `GET /api/watch-pool/groups`、`POST /api/watch-pool/groups`、`DELETE /api/watch-pool/groups/{id}`、`POST /api/watch-pool/items`、`DELETE /api/watch-pool/items/{id}`、行情报价接口。 |
| App 端关注点 | 分组是核心结构；移动端主列表展示核心行情和盈亏，成本、备注、更多行情放详情页；保留从工作台带参高亮能力。 |

### 4.11 布控提醒

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/stock-alert` |
| 浏览权限 | 已登录用户；页面内 admin 额外展示用户、邮箱通知列和“触发检查”按钮。 |
| 页面目标 | 为股票、指数、可转债配置涨跌幅阈值，越界时提醒。 |
| 展示数据 | 关注数量、越界数量、启用数量、类型、名称、最新价、涨跌幅、阈值、启用状态、行情时间、最近提醒、邮箱通知状态。 |
| 功能点 | 按类型筛选；新增/编辑/删除提醒；启用/停用；管理员手动触发检查。 |
| 点击逻辑 | 筛选或刷新重新拉列表；新增默认股票和 5% 阈值；编辑时目标不可改，只改阈值和启用；删除二次确认；admin 触发检查后刷新。 |
| 后端接口 | `GET /api/stock-alerts`、`GET /api/stock-alerts/target-options`、`POST /api/stock-alerts`、`DELETE /api/stock-alerts/{id}`、`POST /api/stock-alerts/check`。 |
| App 端关注点 | 越界状态要突出，不能只靠颜色；普通用户需要看到邮箱通知不可用提示；管理员操作要按角色隐藏。 |

### 4.12 标的分析报告

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/ai-center/scene-reports` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 按标的生成、查看、复用股票/指数/可转债分析报告。 |
| 展示数据 | 报告标的列表、最新状态、历史报告数、最新版本、生成时间、摘要、新建报告配置、历史报告、报告详情正文。 |
| 功能点 | 按名称/代码/类型筛选；创建报告任务；选择配置分组/模板；选择标的；选择报告类型；设置召回片段数和 K 线窗口；调整参数；保存/更新/删除自定义配置；查看详情；进入工作台；历史报告；重新生成；导出 PDF。 |
| 点击逻辑 | 新建报告打开抽屉；模板回填参数；远程搜索标的；提交后启动报告轮询；查看打开详情；工作台带 `reportId` 跳转；重新生成基于原任务提交。 |
| 后端接口 | `GET /api/ai/scene-analysis/tasks/reports/targets`、`POST /api/ai/scene-analysis/tasks`、`GET /api/ai/scene-analysis/config-profiles`、`GET /api/ai/scene-analysis/config-profiles/parameter-schema`、`GET /api/ai/scene-analysis/config-profiles/report-types`、`POST/PUT/DELETE /api/ai/scene-analysis/config-profiles`、`GET /api/ai/scene-analysis/targets/search`、`GET /api/ai/scene-analysis/tasks/reports`、`GET /api/ai/scene-analysis/tasks/reports/{reportId}`、`POST /api/ai/scene-analysis/tasks/{taskNo}/report/regenerate`、`GET /api/ai/scene-analysis/tasks/{taskNo}/report`。 |
| App 端关注点 | 以标的为主线，不以任务为主线；高级参数渐进展示，默认只露核心参数；报告正文里的引用和证据线索要保留。 |

### 4.13 报告工作台

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/ai-center/report-workspace` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 自定义报告和行情组件的研究工作台。 |
| 展示数据 | 布局预设、网格单元、组件类型、标的选择、历史报告选择、报告正文、走势图、盘口数据、详情数据。 |
| 功能点 | 切换布局；拖拽/缩放单元；添加、配置、清空组件；报告组件选择历史报告；行情组件选择标的。 |
| 点击逻辑 | 从报告页带 `reportId` 时默认放入第一个格子；空格子点添加组件；先选组件类型，再选标的；报告主体还需选择历史报告。 |
| 后端接口 | `GET /api/ai/scene-analysis/tasks/reports/{reportId}`、`GET /api/ai/scene-analysis/tasks/reports`、`GET /api/ai/scene-analysis/targets/search`、股票/指数/可转债行情接口。 |
| App 端关注点 | 不必照搬桌面拖拽网格；需要保留“报告 + 行情证据并列”的组件化能力，可用可排序卡片、标签页或组件栈实现。 |

### 4.14 投资心理画像

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/ai-center/investor-psych-profile` |
| 浏览权限 | 已登录用户。 |
| 页面目标 | 通过 10 个交易场景问题生成投资心理画像，影响 AI Chat 的建议风格。 |
| 展示数据 | 问卷进度、题目分组、选项、画像版本、建议强度、波动情绪、决策风格、操作节奏、回答偏好、持仓心态、AI 回答策略摘要。 |
| 功能点 | 选择答案、重置答案、保存画像；首次保存创建，已有画像更新。 |
| 点击逻辑 | 必须完成全部题目才可保存；保存成功后展示画像结果。 |
| 后端接口 | `GET /api/ai/investor-psych-profile/questionnaire`、`GET /api/ai/investor-psych-profile`、`POST /api/ai/investor-psych-profile`、`PUT /api/ai/investor-psych-profile`。 |
| App 端关注点 | 移动端适合逐题或分组卡片；保留“会影响 AI 建议口径”的说明和最终画像摘要。 |

### 4.15 知识库概览

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/knowledge/overview` |
| 浏览权限 | admin；知识库父路由控制。 |
| 页面目标 | 查看知识库规模、文本量、最近入库和标签分布。 |
| 展示数据 | 文档数、知识条目数、总文本量、最近更新、场景类别、标签、条数、同类占比、全局占比。 |
| 功能点 | 标签分布排序；按条数、同类占比、全局占比切换升降序。 |
| 点击逻辑 | 点击排序字段后更新本地排序；数据来自概览接口。 |
| 后端接口 | `GET /api/knowledge/overview`。 |
| App 端关注点 | 保留四个关键指标和标签分布；移动端可用分场景折叠列表或条形占比卡片。 |

### 4.16 知识库材料

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/knowledge/materials` |
| 浏览权限 | admin；知识库父路由控制，后端 `/api/ai/knowledge-material/**` 也要求 admin。 |
| 页面目标 | 按标的或自然语言召回知识库原文，只展示材料，不生成报告。 |
| 展示数据 | 检索方式、报告配置、标的类型、标的、场景口径、K 线窗口、召回数量、参数覆盖、任务状态、场景分组、来源文件/任务、场景标签、综合分、语义分、原文。 |
| 功能点 | 按标的检索；自然语言检索；复用报告配置模板和参数 Schema；提交材料任务；轮询结果；手动刷新；按场景、标签、知识库名称筛选；重置筛选。 |
| 点击逻辑 | 按标的时必须选择标的；自然语言时必须输入问题；提交后创建任务并每 1.6 秒轮询；状态为 `success` 或 `failed` 停止轮询；筛选只影响前端结果展示。 |
| 后端接口 | `POST /api/ai/knowledge-material/tasks`、`GET /api/ai/knowledge-material/tasks/{taskNo}`、复用 `GET /api/ai/scene-analysis/config-profiles`、`GET /api/ai/scene-analysis/config-profiles/parameter-schema`、`GET /api/ai/scene-analysis/config-profiles/report-types`、`GET /api/ai/scene-analysis/targets/search`。 |
| App 端关注点 | 必须明确这是“材料检索”，不是“生成报告”；结果要保留来源、场景中文名、标签中文名、分数和原文；自然语言模式可展示改写后的查询但不应占据结果主线。 |

知识库材料和报告页的关键差异：

| 对比项 | 标的分析报告 | 知识库材料 |
| --- | --- | --- |
| 提交接口 | `POST /api/ai/scene-analysis/tasks` | `POST /api/ai/knowledge-material/tasks` |
| 结果 | 报告正文、版本、历史 | 召回 chunk、来源、标签、原文 |
| 是否生成报告 | 是 | 否 |
| 是否进入报告历史 | 是 | 否 |
| 是否保存配置模板 | 支持保存/更新/删除自定义模板 | 只读取和使用已有模板，不保存模板 |
| App 端主按钮 | 创建报告任务 | 检索材料 |

### 4.17 知识库浏览

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/knowledge/base` |
| 浏览权限 | admin。 |
| 页面目标 | 浏览、筛选和维护向量化后的知识条目。 |
| 展示数据 | 文档数、知识条目、总文本量、最近更新、chunk 列表、全文、场景标签、文件名、任务号、片段序号、页码、段落、置信度、版本、入库时间。 |
| 功能点 | 按来源类型、场景类别、标签、文档名筛选；分页；选择条目；编辑文本；编辑场景标签。 |
| 点击逻辑 | 选择列表项展示详情；保存文本变更会触发重新向量化；只改标签不触发重新向量化。 |
| 后端接口 | `GET /api/knowledge/stats`、`GET /api/knowledge/chunks`、`GET /api/knowledge/chunks/{id}`、`PUT /api/knowledge/chunks/{id}`。 |
| App 端关注点 | 文本编辑和标签编辑必须进入明确编辑态，避免误改知识库内容；详情页要保留来源和置信度。 |

### 4.18 知识库 OCR 导入

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/ai-center/knowledge-processing` |
| 浏览权限 | admin。 |
| 页面目标 | 上传 PDF/图片，进入 OCR、清洗、质量校验、场景打标和向量入库流程。 |
| 展示数据 | 任务统计、六阶段流水线、任务表格、阶段状态、进度、页数、分段数、选中任务详情、场景打标明细、复核文本。 |
| 功能点 | 多文件上传、状态筛选、分页、任务删除、阶段详情、chunk 标签明细、重新打标、进入人工复核。 |
| 点击逻辑 | 上传前校验格式和 50MB 限制；点击任务行切换详情；点击阶段查看阶段详情；需要人工介入时跳转 OCR 复核；重新打标提交修订段落。 |
| 后端接口 | `POST /api/ai/ocr/tasks/page`、`POST /api/ai/ocr/tasks`、`GET /api/ai/ocr/tasks/{taskNo}/stages`、`GET /api/ai/ocr/tasks/{taskNo}/chunk-tags`、`POST /api/ai/ocr/tasks/delete`、`GET /api/ai/ocr/reviews/{taskNo}`、`POST /api/ai/ocr/reviews/{taskNo}/submit`。 |
| App 端关注点 | 上传、任务队列、阶段详情可拆页面；必须保留处理中、失败、需人工复核等状态。 |

### 4.19 OCR 人工复核

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/ai-center/ocr-review/:taskNo?` |
| 浏览权限 | admin；菜单隐藏，从 OCR 导入页进入。 |
| 页面目标 | 对 OCR 段落人工修订，对照原始页图后保存草稿或确认入库。 |
| 展示数据 | 任务号、置信度、段落数、警告数、低置信度数、复核状态、段落文本、页码、字数、警告、右侧页图。 |
| 功能点 | 编辑段落、上移/下移、合并下一段、复制、删除、保存草稿、提交复核。 |
| 点击逻辑 | 选择段落联动显示来源页图；段落操作后重新编号并更新统计；提交后返回 OCR 队列。 |
| 后端接口 | `GET /api/ai/ocr/reviews/{taskNo}`、`GET /api/ai/ocr/reviews/{taskNo}/pages/{pageNo}/image`、`PUT /api/ai/ocr/reviews/{taskNo}/draft`、`POST /api/ai/ocr/reviews/{taskNo}/submit`。 |
| App 端关注点 | 核心是“段落文本 + 原图证据”对照；编辑动作要有保存草稿和提交入库的清晰区分。 |

### 4.20 手动知识导入

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/ai-center/manual-knowledge` |
| 浏览权限 | admin。 |
| 页面目标 | 手动录入知识 chunk，复用场景打标和向量入库流程。 |
| 展示数据 | 草稿数、处理中数、已完成数、当前页 chunk 数、任务编号、标题、状态、阶段、进度、chunk 数、更新时间。 |
| 功能点 | 新增手动知识、标题编辑、多 chunk 编辑、保存草稿、提交入库、查看/编辑已有任务、删除任务、状态筛选和分页。 |
| 点击逻辑 | 草稿可编辑，非草稿只读；提交前至少一个非空 chunk；无标题时后端使用首个 chunk 前若干字。 |
| 后端接口 | `POST /api/ai/manual-knowledge/tasks/page`、`POST /api/ai/manual-knowledge/tasks`、`GET /api/ai/manual-knowledge/tasks/{taskNo}`、`PUT /api/ai/manual-knowledge/tasks/{taskNo}/draft`、`POST /api/ai/manual-knowledge/tasks/{taskNo}/submit`、`POST /api/ai/manual-knowledge/tasks/delete`。 |
| App 端关注点 | chunk 是最小录入单元；草稿和提交入库要明显区分；适合“任务列表 + chunk 编辑页”。 |

### 4.21 系统监控

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/analytics` |
| 浏览权限 | admin；挂在系统配置父菜单下。 |
| 页面目标 | 查看访问日志、AI Token 和行情同步任务是否正常写入。 |
| 展示数据 | 近 30 天访问、独立用户、AI 调用、全量同步、Token 趋势、访问趋势、同步任务成功/失败/运行中摘要。 |
| 功能点 | 刷新监控、状态标签、状态点、骨架屏。 |
| 点击逻辑 | 页面进入静默加载；点击刷新并行拉取四类数据；失败提示。 |
| 后端接口 | `GET /api/ai/console/overview`、`GET /api/ai/token-usage/trends`、`GET /api/ai/console/visit-trends`、`GET /api/market-sync/jobs/latest-full`。 |
| App 端关注点 | 系统状态要服务“是否可用”的判断，保留最近时间、失败原因和运行中状态。 |

### 4.22 Token 用量

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/token-usage` |
| 浏览权限 | admin。 |
| 页面目标 | 查看模型调用消耗、来源归因和响应记录。 |
| 展示数据 | 总费用、请求次数、总 Token、缓存 Token、推理 Token、趋势图、日志表、详情信息。 |
| 功能点 | 按时间范围、来源、阶段、模型、用户名筛选；分页；查看详情。 |
| 点击逻辑 | 查询重置页码并刷新日志；分页只刷新日志；详情打开消耗拆分和响应信息。 |
| 后端接口 | `GET /api/ai/token-usage/overview`、`GET /api/ai/token-usage/trends`、`GET /api/ai/token-usage/logs`。 |
| App 端关注点 | 费用和来源优先级高；移动端日志表建议做卡片列表和详情页。 |

### 4.23 标的配置

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/target-config` |
| 浏览权限 | admin。 |
| 页面目标 | 维护股票、指数、可转债基础标的。 |
| 展示数据 | 新增股票、新增可转债、新增指数预留、删除标的表单、同步结果。 |
| 功能点 | 新增股票；新增可转债；删除股票/指数/可转债；代码校验。 |
| 点击逻辑 | 新增校验 6 位代码后提交；成功显示同步结果并清空表单；删除需要二次确认，输入代码前按钮禁用。 |
| 后端接口 | `POST /api/system-config/stocks`、`POST /api/system-config/bonds`、`POST /api/system-config/targets/delete`。 |
| App 端关注点 | 物理删除是高危操作，必须强确认并说明会删除行情、报告、提醒、观察池等关联数据；指数新增当前只是预留入口。 |

### 4.24 数据同步

| 项 | 内容 |
| --- | --- |
| Web 路由 | `/data-sync` |
| 浏览权限 | admin。 |
| 页面目标 | 同步股票、指数、可转债行情数据，并查看最近任务状态。 |
| 展示数据 | 股票/指数/转债同步健康卡、全量同步任务、指定标的补数表单、最近完成时间、耗时、失败原因。 |
| 功能点 | 全量同步三类行情；指定标的补分时或 K 线；轮询同步状态；查看最近全量任务。 |
| 点击逻辑 | 进入页面静默刷新状态；触发全量同步后每 3 秒轮询，最多 120 次；仍运行则提示后台执行；单标的同步要求代码非空并规范化条数。 |
| 后端接口 | `POST /api/stocks/sync`、`GET /api/stocks/sync/status`、`POST /api/stocks/sync/trends/{stockCode}`、`POST /api/stocks/sync/daily-klines/{stockCode}`、`POST /api/indices/sync`、`GET /api/indices/sync/status`、`POST /api/indices/sync/klines/{indexCode}`、`POST /api/bonds/sync`、`GET /api/bonds/sync/status`、`POST /api/bonds/sync/klines/{bondCode}`、`GET /api/market-sync/jobs/latest-full`。 |
| App 端关注点 | 长任务不能阻塞 App；保留运行中、成功、失败、最近时间和后台执行提示。 |

## 5. App 端状态和交互统一规则

| 场景 | 统一规则 |
| --- | --- |
| 未登录 | 跳登录页，登录成功后回原目标页。 |
| 无权限 | 显示无权限页或隐藏入口；接口返回 403 时不要静默失败。 |
| 空数据 | 提供明确空态，例如“暂无观察池数据”“提交检索后展示知识库材料”。 |
| 加载中 | 首屏数据使用骨架屏或加载态；按钮级操作使用按钮 loading。 |
| 长任务 | OCR、报告生成、知识库材料、数据同步需要展示状态和轮询中提示。 |
| 失败 | 展示失败原因；任务类页面保留失败状态，不直接清空结果。 |
| 高风险操作 | 删除标的、删除知识任务、提交复核入库、物理删除数据需要二次确认。 |
| 金融涨跌 | 保持 A 股语义色：上涨红、下跌绿；不要只靠颜色表达风险状态。 |

## 6. 接口前缀说明

前端 API 封装中大多省略 `/api` 前缀，由请求客户端统一拼接；本文接口统一写成后端完整路径 `/api/...`，便于 App 联调和权限核对。

## 7. 代码依据

- 前端路由：`frontend-vue/apps/web-ele/src/router/routes/core.ts`
- 业务路由：`frontend-vue/apps/web-ele/src/router/routes/modules/dashboard.ts`
- AI 路由：`frontend-vue/apps/web-ele/src/router/routes/modules/ai-center.ts`
- 知识库路由：`frontend-vue/apps/web-ele/src/router/routes/modules/knowledge.ts`
- 导航分组：`frontend-vue/apps/web-ele/src/layouts/basic/navigation-workspaces.ts`
- 前端 API：`frontend-vue/apps/web-ele/src/api/**`
- 后端权限：`backend-java/finance-security/src/main/java/com/scrapider/finance/config/SecurityConfig.java`
