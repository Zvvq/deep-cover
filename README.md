# Deep Cover — 社交推理游戏

Deep Cover 是一款基于 Spring Boot 构建的多人实时社交推理（Social Deduction）游戏后端。玩家通过房间码加入房间，以匿名身份参与聊天和投票，目标是找出隐藏在玩家中的 AI 卧底。

---

## 项目概述

- **游戏类型**：多人实时社交推理网页游戏
- **玩家人数**：2-8 名真人玩家 + 自动加入的 AI 玩家
- **核心玩法**：匿名聊天、推理分析、投票淘汰、身份揭露
- **架构特点**：Spring Boot 单体 Web 应用，通过 WebSocket 实现实时消息同步

## 游戏模式

| 模式 | 说明 |
|------|------|
| `CHAT_UNDERCOVER` | **聊天卧底模式** — 玩家围绕公开话题进行匿名聊天，通过发言内容和投票找出 AI 卧底 |
| `WORD_UNDERCOVER` | **关键词卧底模式** — 每位玩家获得一个关键词，轮流描述自己的词，通过描述差异找出持有不同词语的卧底 |

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5.8 |
| 实时通信 | WebSocket (STOMP) |
| 数据存储 | Redis (Spring Data Redis) |
| 分布式锁 | Redisson 3.50.0 |
| 构建工具 | Maven |
| 测试框架 | JUnit 5 + Testcontainers |

## 项目结构

```
src/main/java/com/cqie/deepcover/
├── agent/              # AI Agent 集成模块
│   ├── event/          #   Agent 事件类型与载荷
│   ├── internal/       #   Agent 内部接口（供 Python Agent 服务调用）
│   └── push/           #   Agent 事件推送服务
├── chat/               # 实时聊天模块
│   ├── config/         #   WebSocket/STOMP 配置
│   ├── controller/     #   聊天消息接口（WebSocket + HTTP）
│   ├── enums/          #   房间事件类型枚举
│   ├── interfaces/     #   消息仓储与事件发布接口
│   ├── record/         #   聊天消息 DTO
│   └── service/        #   聊天业务逻辑
├── game/               # 游戏计时与流程控制模块
│   ├── config/         #   计时器配置
│   ├── controller/     #   计时器查询接口
│   ├── enums/          #   游戏阶段、状态枚举
│   ├── interfaces/     #   计时器仓储接口
│   ├── listener/       #   游戏事件监听器
│   ├── record/         #   计时器快照 DTO
│   ├── scheduler/      #   计时器调度任务
│   └── service/        #   计时器业务逻辑
├── redis/              # Redis 基础设施
│   ├── config/         #   Redisson 配置
│   ├── lock/           #   分布式锁执行器
│   └── support/        #   JSON 序列化、TTL 管理
├── room/               # 房间管理模块
│   ├── controller/     #   房间 REST API
│   ├── enums/          #   房间状态、游戏模式、玩家类型等枚举
│   ├── exception/      #   房间业务异常
│   ├── interfaces/     #   房间仓储接口
│   ├── model/          #   房间领域模型
│   ├── record/         #   房间快照、玩家信息 DTO
│   └── service/        #   房间生命周期管理
├── topic/              # 话题管理模块
│   ├── interfaces/     #   话题仓储接口
│   ├── record/         #   话题 DTO
│   └── service/        #   话题选择逻辑
├── vote/               # 投票决策模块
│   ├── controller/     #   投票 REST API
│   ├── enums/          #   游戏胜利者枚举
│   ├── interfaces/     #   投票仓储接口
│   ├── listener/       #   投票事件监听器
│   ├── record/         #   投票会话、结果 DTO
│   └── service/        #   投票结算、淘汰与胜负判断
└── word/               # 关键词卧底子模块
    ├── controller/     #   词语查询与描述提交 API
    ├── interfaces/     #   词语分配与描述仓储接口
    ├── record/         #   词语对、描述条目 DTO
    └── service/        #   词语分配与描述管理
```

## 核心 API

### 房间管理

```http
POST   /api/rooms                        # 创建房间
POST   /api/rooms/{roomCode}/join        # 加入房间
POST   /api/rooms/{roomCode}/start       # 开始游戏（仅房主）
POST   /api/rooms/{roomCode}/leave       # 离开房间
GET    /api/rooms/{roomCode}/snapshot    # 获取房间状态快照
```

### 聊天消息

```http
GET    /api/rooms/{roomCode}/messages    # 获取历史消息
```

WebSocket（STOMP）：
- 连接：`/ws`
- 发送消息：`/app/rooms/{roomCode}/chat`
- 订阅事件：`/topic/rooms/{roomCode}/events`

### 投票决策

```http
POST   /api/rooms/{roomCode}/votes       # 提交投票
GET    /api/rooms/{roomCode}/votes       # 获取投票快照
```

### 游戏计时器

```http
GET    /api/rooms/{roomCode}/timer       # 查询计时器状态
```

### 关键词卧底模式

```http
POST   /api/rooms/{roomCode}/word/me              # 查询我的关键词
POST   /api/rooms/{roomCode}/word/descriptions     # 提交词语描述
GET    /api/rooms/{roomCode}/word/descriptions     # 获取描述快照
```

> 所有需要身份验证的请求均通过 `X-Player-Token` 请求头传递玩家凭证。

## 房间生命周期状态

```
WAITING → DESCRIBING → CHATTING → VOTING → ENDED
    │                                          │
    └──────────── DESTROYED ───────────────────┘
```

| 状态 | 说明 |
|------|------|
| `WAITING` | 等待玩家加入，房主可控制开始游戏 |
| `DESCRIBING` | 关键词卧底模式的描述阶段 |
| `CHATTING` | 聊天阶段，玩家围绕话题讨论 |
| `VOTING` | 投票阶段，玩家投票淘汰嫌疑人 |
| `ENDED` | 游戏结束，显示胜负结果 |
| `DESTROYED` | 房间已销毁（房主离开触发） |

## 实时事件类型

通过 WebSocket 订阅 `/topic/rooms/{roomCode}/events` 接收以下事件：

- `PLAYER_JOINED` — 玩家加入房间
- `PLAYER_LEFT` — 玩家离开房间
- `ROOM_DESTROYED` — 房间被销毁
- `GAME_STARTED` — 游戏开始
- `TOPIC_PROMPT` — 话题/引导问题发布
- `CHAT_MESSAGE` — 新聊天消息
- `VOTING_STARTED` — 投票阶段开始
- `VOTE_UPDATED` — 投票状态更新
- `PLAYER_ELIMINATED` — 玩家被淘汰
- `ROUND_STARTED` — 新一轮开始
- `GAME_ENDED` — 游戏结束
- `WORD_ROUND_STARTED` — 关键词轮次开始
- `WORD_DESCRIPTION_SUBMITTED` — 词语描述已提交

## 配置说明

主要配置项（`application.properties`）：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `deep-cover.room.repository` | 房间仓储实现 | `redis` |
| `deep-cover.game-timer.repository` | 计时器仓储实现 | `redis` |
| `deep-cover.vote.repository` | 投票仓储实现 | `redis` |
| `deep-cover.chat.repository` | 聊天仓储实现 | `redis` |
| `deep-cover.word-assignment.repository` | 词语分配仓储实现 | `redis` |
| `deep-cover.word-description.repository` | 词语描述仓储实现 | `redis` |
| `deep-cover.lock.provider` | 分布式锁提供者 | `redisson` |
| `deep-cover.redis.room-ttl` | 活跃房间 TTL | `PT2H` |
| `deep-cover.redis.ended-room-ttl` | 已结束房间 TTL | `PT10M` |
| `deep-cover.agent.enabled` | 是否启用 Agent 集成 | `true` |
| `deep-cover.agent.base-url` | Agent 服务地址 | `http://localhost:8000` |
| `spring.data.redis.host` | Redis 主机 | `localhost` |
| `spring.data.redis.port` | Redis 端口 | `6379` |

> 测试环境使用 `memory` 仓储和 `none` 锁提供者，无需 Redis 依赖。

## 快速开始

### 前置条件

- Java 17+
- Maven 3.6+
- Redis 6.0+（生产环境）

### 运行项目

```bash
# 启动 Redis（如未运行）
redis-server

# 编译并运行
mvn spring-boot:run
```

应用启动后访问 `http://localhost:8080` 进入游戏界面。

### 运行测试

```bash
# 运行全部测试
mvn test

# 运行特定模块测试
mvn test -Dtest=RoomServiceTest
mvn test -Dtest=VoteServiceTest
mvn test -Dtest=ChatServiceTest
```

### 构建可执行 JAR

```bash
mvn clean package
java -jar target/deep-cover-0.0.1-SNAPSHOT.jar
```

## AI Agent 集成

项目通过 HTTP 接口与外部 Python FastAPI Agent 服务集成：

- **发言决策**：Agent 根据聊天上下文决定是否发言及发言内容
- **投票决策**：Agent 根据候选人和聊天记录选择投票目标
- **内部接口**：Agent 服务可通过内部认证接口获取房间状态、提交消息和投票

Agent 服务不可用时，系统会自动降级：发言失败则跳过 AI 消息，投票失败则随机选择合法目标。

> Agent 端项目：[deep-cover-agent](https://github.com/Zvvq/deep-cover-agent)

## 游戏规则

### 聊天卧底模式（CHAT_UNDERCOVER）

1. 2-8 名真人玩家加入房间，房主开始游戏
2. 系统自动加入 1 名 AI 玩家，为所有玩家分配匿名序号和颜色
3. 每轮展示一个公开话题，玩家进行 5 分钟匿名聊天
4. 聊天结束后进入投票阶段，玩家投票淘汰最可疑的人
5. 结算投票：被投出者淘汰，检查胜负条件
6. **真人胜利**：AI 被淘汰
7. **AI 胜利**：存活 AI 数量 ≥ 存活真人数量

### 关键词卧底模式（WORD_UNDERCOVER）

1. 玩家加入房间并开始游戏后，每人获得一个关键词（含编号和颜色）
2. 玩家按顺序轮流用一句话描述自己的关键词
3. 描述阶段结束后进入聊天讨论阶段
4. 聊天结束后投票淘汰嫌疑人
5. 胜负规则与聊天卧底模式相同

## 许可证

本项目仅供学习使用。
