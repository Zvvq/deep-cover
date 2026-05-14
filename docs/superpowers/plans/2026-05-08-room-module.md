# Room 模块实现计划

> **给后续执行者：** 按任务逐步执行，使用 checkbox（`- [ ]`）跟踪进度。

**目标：** 实现 `room` 模块，覆盖房间创建、加入、快照、开始校验、离开和房间销毁行为。

**架构：** 在 `com.cqie.deepcover.room` 下新增独立 Spring Boot REST 模块。该模块用内存保存房间，返回 `playerToken`，校验房主权限，并暴露 REST 接口。后续 `game`、`chat`、`web` 模块会依赖这里的 API。

**技术栈：** Java 17、Spring Boot 3.5.8、Maven、JUnit 5、MockMvc。

---

### 任务 1：恢复构建基线

**文件：**
- 新建：`pom.xml`

- [ ] 新增 Maven Spring Boot 构建配置，使用 Java 17、`spring-boot-starter-web` 和 `spring-boot-starter-test`。
- [ ] 运行 `mvn test`，确认已有 Spring Boot 上下文测试通过。

### 任务 2：Room 领域模型和仓库

**文件：**
- 新建：`src/main/java/com/cqie/deepcover/room/enums/RoomStatus.java`
- 新建：`src/main/java/com/cqie/deepcover/room/enums/PlayerType.java`
- 新建：`src/main/java/com/cqie/deepcover/room/record/Player.java`
- 新建：`src/main/java/com/cqie/deepcover/room/model/Room.java`
- 新建：`src/main/java/com/cqie/deepcover/room/interfaces/RoomRepository.java`
- 新建：`src/main/java/com/cqie/deepcover/room/interfaces/impl/InMemoryRoomRepository.java`
- 测试：`src/test/java/com/cqie/deepcover/room/interfaces/impl/InMemoryRoomRepositoryTest.java`

- [ ] 先写失败的仓库测试，覆盖保存、查找、删除和房间码生成不冲突。
- [ ] 实现领域对象和内存仓库。
- [ ] 运行仓库测试并确认通过。

### 任务 3：Room Service

**文件：**
- 新建：`src/main/java/com/cqie/deepcover/room/service/RoomService.java`
- 新建：`src/main/java/com/cqie/deepcover/room/exception/RoomException.java`
- 新建：`src/main/java/com/cqie/deepcover/room/enums/RoomErrorCode.java`
- 测试：`src/test/java/com/cqie/deepcover/room/service/RoomServiceTest.java`

- [ ] 先写失败的 service 测试，覆盖创建、加入、快照、仅房主可开始、至少 2 名真人才能开始、开始后不能加入、玩家离开、房主离开销毁房间。
- [ ] 实现满足测试的最小 service 方法。
- [ ] 运行 service 测试并确认通过。

### 任务 4：Room REST API

**文件：**
- 新建：`src/main/java/com/cqie/deepcover/room/controller/RoomController.java`
- 新建：`src/main/java/com/cqie/deepcover/room/record/RoomDtos.java`
- 新建：`src/main/java/com/cqie/deepcover/room/exception/RoomExceptionHandler.java`
- 测试：`src/test/java/com/cqie/deepcover/room/controller/RoomControllerTest.java`

- [ ] 先写失败的 MockMvc 测试，覆盖 `POST /api/rooms`、`POST /api/rooms/{roomCode}/join`、`POST /api/rooms/{roomCode}/start`、`POST /api/rooms/{roomCode}/leave`、`GET /api/rooms/{roomCode}/snapshot`。
- [ ] 实现 REST DTO、controller 方法和异常到 HTTP 状态码的映射。
- [ ] 运行 controller 测试并确认通过。

### 任务 5：验证

**文件：**
- 如果验证暴露问题，只修改任务 1-4 创建的相关文件。

- [ ] 运行 `mvn test`。
- [ ] 运行 `git status --short`，确认只包含预期的 room 模块文件、`pom.xml`、计划文档和 `.gitignore`。
