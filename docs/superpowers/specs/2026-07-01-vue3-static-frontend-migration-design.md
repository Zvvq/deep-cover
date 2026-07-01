# Deep Cover Vue3 前端迁移设计

## 目标

将当前托管在 `src/main/resources/static` 下的手写静态前端，完整迁移为 Vue3 + Vite 工程。迁移后的前端仍由 Spring Boot 提供静态资源，但源码不再直接维护在 `static` 目录中；`static` 只保存构建产物。

本次重构保留现有游戏能力：创建/加入房间、等待室、房主开始游戏、聊天卧底、关键词卧底、计时、投票、房间状态恢复、STOMP 实时事件和本地凭证恢复。后端 REST、WebSocket 路径和数据结构保持兼容。

## 范围

包含：

- 新增 `src/main/frontend` 作为 Vue3 源码目录。
- 新增 `package.json`、`vite.config.js` 和必要的前端构建配置。
- 使用 Vue3 Composition API 组织状态、派生数据和副作用。
- 将当前三个互斥视图拆成 Vue 组件：大厅、等待室、游戏主界面。
- 将游戏主界面继续拆为聊天、玩家列表、计时状态、话题、关键词描述、投票等面板。
- 将 REST、STOMP、localStorage、计时器逻辑拆为 composables 或服务模块。
- 使用 `ui-ux-pro-max` 给出的深色战术游戏设计系统，重做样式和响应式布局。
- 更新静态前端测试，使其验证 Vue 构建产物和关键能力标记。

不包含：

- 修改后端业务 API、事件类型或鉴权方式。
- 引入 Vue Router 或 Pinia。
- 引入重型 UI 组件库。
- 新增登录、账号、历史复盘、观战、管理后台等产品功能。
- 使用 3D/WebGL、复杂持续动画或装饰性大背景。

## 推荐方案

采用 Vue3 + Vite + Composition API，不引入 Router/Pinia。

原因：

- 当前前端是单页实时游戏，只有大厅、等待室、游戏中三个主状态，路由不会显著降低复杂度。
- 主要复杂度来自实时事件、副作用、房间状态恢复和不同游戏阶段渲染，Composition API 足以表达这些边界。
- 依赖少，构建链清晰，便于由 Spring Boot 继续托管产物。
- 可以一次性放弃原 `app.js` 手写 DOM 操作，同时避免一次引入过多架构层。

## 工程结构

建议结构：

```text
package.json
vite.config.js
src/main/frontend/
  index.html
  src/
    main.js
    App.vue
    styles/
      tokens.css
      base.css
      components.css
    components/
      HomeView.vue
      WaitingRoomView.vue
      GameView.vue
      RoomCodeBadge.vue
      PlayerList.vue
      TimerBadge.vue
      TopicPanel.vue
      WordPanel.vue
      VotePanel.vue
      ChatPanel.vue
      StatusToast.vue
    composables/
      useRoomSession.js
      useRoomApi.js
      useRoomSocket.js
      useGameTimer.js
      useGameFlow.js
      useClipboard.js
    domain/
      labels.js
      playerColors.js
      gamePhases.js
```

`vite.config.js` 输出到 `src/main/resources/static`。生产构建时，旧的 `index.html`、`app.js`、`styles.css` 不再作为源码维护，而是由 Vite 生成新的 `index.html` 和版本化 assets。

## 组件设计

`App.vue` 是唯一顶层容器，负责装配全局 session、当前视图和 toast。

`HomeView.vue` 负责：

- 选择游戏模式。
- 创建房间。
- 输入 6 位房间号加入房间。
- 展示创建/加入中的 loading 和错误。

`WaitingRoomView.vue` 负责：

- 展示房间号、复制按钮、玩家列表、房间状态。
- 展示房主可见的开始按钮。
- 展示人数不足提示。
- 调用离开房间和开始游戏动作。

`GameView.vue` 负责游戏页面布局：

- 顶部状态栏：房间号、计时器、阶段标签。
- 中央主区域：话题、关键词描述、投票和聊天。
- 侧边或移动端折叠区域：玩家列表。
- 根据房间状态显示 `DESCRIBING`、`CHATTING`、`VOTING`、`ENDED` 的对应面板。

`ChatPanel.vue` 负责：

- 历史消息和实时消息渲染。
- 自己消息靠右、他人消息靠左、系统事件居中。
- 禁言状态和已淘汰状态下的输入禁用。
- 发送按钮 loading/disabled 状态。

`WordPanel.vue` 负责：

- 当前玩家关键词展示。
- 当前描述轮次、轮到谁描述、已提交描述列表。
- 当前玩家描述输入和提交状态。

`VotePanel.vue` 负责：

- 投票候选人列表。
- 已投票、不可投票、提交中状态。
- 不公开投票对象，只显示提交完成和进度。

## 状态与数据流

`useRoomSession` 持有核心响应式状态：

- `roomCode`
- `playerId`
- `playerToken`
- `currentRoomStatus`
- `currentGameMode`
- `currentPlayers`
- `currentTopic`
- `currentPlayerWord`
- `wordDescriptionSnapshot`
- `voteSnapshot`
- `messages`

该 composable 负责 localStorage 的读写和清空。键名继续使用当前前端的 `dc_roomCode`、`dc_playerId`、`dc_playerToken`，避免用户刷新后丢失已有会话。

`useRoomApi` 封装所有 REST 调用：

- `createRoom(gameMode)`
- `joinRoom(roomCode)`
- `startGame(roomCode)`
- `leaveRoom(roomCode)`
- `snapshot(roomCode)`
- `messages(roomCode)`
- `timer(roomCode)`
- `votesSnapshot(roomCode)`
- `castVote(roomCode, targetPlayerId)`
- `playerWord(roomCode)`
- `wordDescriptions(roomCode)`
- `submitWordDescription(roomCode, description)`

所有需要身份的请求继续发送 `X-Player-Token`。

`useRoomSocket` 封装 STOMP：

- 连接 `/ws`。
- 订阅 `/topic/rooms/{roomCode}/events`。
- 向 `/app/rooms/{roomCode}/chat` 发送聊天。
- 把事件交给 `useGameFlow` 处理。
- 在离开房间、房间销毁或组件卸载时断开连接。

`useGameFlow` 负责将快照和事件归一化到响应式状态：

- 房间快照是权威状态。
- WebSocket 事件用于低延迟增量更新。
- 关键阶段切换后主动补拉必要快照，例如投票快照、关键词、描述快照和计时器。
- 如果 WebSocket 暂时断开，保留 STOMP 自动重连，并在重连或定时刷新后使用房间快照校正状态。

`useGameTimer` 负责：

- 使用服务端 `endsAt` 和本地 offset 计算剩余时间。
- 每秒更新显示。
- 状态离开计时阶段或组件卸载时清理 interval。
- 到期后显示阶段已结束提示，等待服务端事件或刷新校正。

## 视觉设计

设计系统采用 `ui-ux-pro-max` 推荐的 Card & Board Game 深色方向：

- 主色：`#15803D`
- 次色：`#166534`
- 强调色：`#D97706`
- 背景：`#0F172A`
- 卡片：`#192134`
- muted surface：`#0F1F2B`
- 文本：高对比白色和 `#94A3B8`
- 危险色：`#DC2626`

页面观感是“轻量战术控制台”，不是营销页，也不是重型 3D 游戏界面。具体规则：

- 首页直接呈现创建/加入房间工作流，不做落地页 hero。
- 游戏页优先信息密度和可扫读性。
- 卡片圆角控制在 8px 左右，主要用于面板和列表项。
- 图标使用内联 SVG 或后续可替换为统一图标库，禁止用 emoji 作为结构图标。
- 动画限制在 150-300ms 的状态反馈，避免持续装饰动画。
- 支持 `prefers-reduced-motion`。
- 保留明确的 focus-visible 样式和 44px 以上点击目标。
- 移动端采用单列布局，聊天输入固定在底部时必须给内容区留出安全空间。

## 错误处理

前端错误处理分三层：

- 表单级错误：房间号不合法、创建失败、加入失败、描述为空等，显示在对应表单附近。
- 操作级 toast：复制房间号、开始游戏失败、投票提交失败、连接断开等。
- 流程级恢复：快照拉取失败或房间不存在时，清理 session 并回到大厅。

重复提交通过 loading 和 disabled 状态处理。所有异步按钮在请求中不可再次点击。

## 测试策略

前端测试分为静态契约和构建验证：

- `npm run build` 必须能生成 `src/main/resources/static/index.html` 和 assets。
- 静态契约测试验证构建后的 HTML 包含 `Deep Cover`、`/api/rooms`、Vue 应用挂载点和版本化模块脚本。
- 静态契约测试验证源码中仍包含关键后端路径、事件类型和功能函数名或导出名。
- Spring `StaticFrontendTest` 更新为不再期待 `app.js`，而是期待 Vite module/asset 产物和 `/api/rooms` 配置。

后端行为测试不在本次迁移中扩展，但迁移完成后需要运行 Maven 测试，确认 Spring 静态资源仍可访问。

## 迁移顺序

1. 新建 Vite Vue3 工程配置和源码目录。
2. 搭建 `App.vue`、全局样式 token 和基础布局。
3. 迁移 REST API 与 session 持久化。
4. 迁移大厅和等待室。
5. 迁移游戏主界面、聊天和玩家列表。
6. 迁移计时器、投票、关键词描述。
7. 迁移 STOMP 事件处理和状态恢复。
8. 配置 Vite 输出到 Spring static。
9. 更新前端契约测试和 Spring 静态资源测试。
10. 运行前端构建、静态契约测试和 Maven 测试。

## 风险与约束

- 当前 `src/main/resources/static` 中的文件是生产入口，替换时必须保证 Spring Boot 仍能直接访问 `/index.html`。
- 网络受限环境下安装 npm 依赖可能需要用户批准。
- 旧静态契约测试针对 `app.js` 和 `styles.css`，迁移后必须同步更新，否则测试会错误失败。
- STOMP 客户端依赖应进入 npm 依赖，不再通过 CDN 脚本引入。
- 构建输出会改写 `src/main/resources/static`，实施前需要确认只移除旧前端产物，不影响其他资源。

## 验收标准

- 项目包含真实 Vue3 + Vite 工程，而不是 CDN 直引 Vue。
- `src/main/resources/static` 不再维护原手写 `app.js` 和 `styles.css` 源码。
- 构建后的页面可由 Spring Boot 静态资源机制访问。
- 大厅、等待室、聊天、投票、关键词描述和计时器能力保持。
- 刷新页面后仍能基于 localStorage 恢复房间状态。
- UI 满足深色战术游戏风格，移动端布局不遮挡核心操作。
- 前端构建、静态契约测试和 Maven 测试通过，或明确记录无法运行的原因。
