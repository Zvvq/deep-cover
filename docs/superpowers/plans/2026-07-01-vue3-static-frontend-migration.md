# Vue3 Static Frontend Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-written static frontend with a Vue3 + Vite application whose production build is served from `src/main/resources/static`.

**Architecture:** Vue source lives under `src/main/frontend`; Vite writes build output into Spring Boot's static resources directory. A single top-level Vue app controls lobby, waiting room, and game view. Composables isolate REST, STOMP, session persistence, game flow, timer, and clipboard side effects.

**Tech Stack:** Vue 3, Vite, `@stomp/stompjs`, native CSS, Node contract tests, Spring Boot static resource tests.

---

## Scope Check

The approved spec covers one subsystem: browser frontend migration. It does not require backend API, event, or authentication changes.

## File Structure

- Create: `package.json` - npm scripts and Vue/Vite/STOMP dependencies.
- Create: `vite.config.js` - Vite build output targeting `src/main/resources/static`.
- Create: `src/main/frontend/index.html` - source HTML with Vue mount point and `/api/rooms` marker.
- Create: `src/main/frontend/src/main.js` - Vue bootstrap.
- Create: `src/main/frontend/src/App.vue` - top-level orchestration.
- Create: `src/main/frontend/src/styles/tokens.css` - design tokens from `ui-ux-pro-max`.
- Create: `src/main/frontend/src/styles/base.css` - reset, form, focus, reduced-motion rules.
- Create: `src/main/frontend/src/styles/components.css` - shared layout and component styles.
- Create: `src/main/frontend/src/domain/gamePhases.js` - room status, game mode, event constants.
- Create: `src/main/frontend/src/domain/playerColors.js` - player color palette helpers.
- Create: `src/main/frontend/src/domain/labels.js` - UI labels and format helpers.
- Create: `src/main/frontend/src/composables/useRoomSession.js` - localStorage-backed session state.
- Create: `src/main/frontend/src/composables/useRoomApi.js` - REST client.
- Create: `src/main/frontend/src/composables/useClipboard.js` - copy helper.
- Create: `src/main/frontend/src/composables/useGameTimer.js` - timer snapshot and local ticking.
- Create: `src/main/frontend/src/composables/useRoomSocket.js` - STOMP connection and chat publish.
- Create: `src/main/frontend/src/composables/useGameFlow.js` - snapshot and websocket event reducer.
- Create: `src/main/frontend/src/components/HomeView.vue` - create/join room.
- Create: `src/main/frontend/src/components/WaitingRoomView.vue` - waiting room.
- Create: `src/main/frontend/src/components/GameView.vue` - game screen layout.
- Create: `src/main/frontend/src/components/RoomCodeBadge.vue` - room code copy control.
- Create: `src/main/frontend/src/components/PlayerList.vue` - players.
- Create: `src/main/frontend/src/components/TimerBadge.vue` - timer.
- Create: `src/main/frontend/src/components/TopicPanel.vue` - topic.
- Create: `src/main/frontend/src/components/ChatPanel.vue` - chat.
- Create: `src/main/frontend/src/components/WordPanel.vue` - keyword descriptions.
- Create: `src/main/frontend/src/components/VotePanel.vue` - voting.
- Create: `src/main/frontend/src/components/StatusToast.vue` - toast.
- Modify: `src/test/resources/static-frontend-contract.test.mjs` - Vue source and Vite output contract.
- Modify: `src/test/java/com/cqie/deepcover/web/StaticFrontendTest.java` - generated static frontend contract.

### Task 1: Scaffold Vue3 and Vite Build

**Files:**
- Create: `package.json`
- Create: `vite.config.js`
- Create: `src/main/frontend/index.html`
- Create: `src/main/frontend/src/main.js`
- Create: `src/main/frontend/src/App.vue`
- Create: `src/main/frontend/src/styles/tokens.css`
- Create: `src/main/frontend/src/styles/base.css`
- Create: `src/main/frontend/src/styles/components.css`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Write the failing static contract**

Replace `src/test/resources/static-frontend-contract.test.mjs` with assertions that read `package.json`, `vite.config.js`, `src/main/frontend/index.html`, `src/main/frontend/src/main.js`, and `src/main/frontend/src/App.vue`. Assert that Vue, `@vitejs/plugin-vue`, and `@stomp/stompjs` are declared; Vite outputs to `src/main/resources/static`; source HTML contains `id="app"` and `/api/rooms`; `main.js` calls `createApp(App)`; `App.vue` references `HomeView`, `WaitingRoomView`, and `GameView`; built `index.html` exists and contains `Deep Cover`, `type="module"`, and no `app.js?v=` reference.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `node src/test/resources/static-frontend-contract.test.mjs`

Expected: FAIL with `ENOENT` for `package.json` or `vite.config.js`.

- [ ] **Step 3: Create npm and Vite config**

Create `package.json` with scripts `dev`, `build`, `test:static`; dependencies `vue` and `@stomp/stompjs`; dev dependencies `vite` and `@vitejs/plugin-vue`.

Create `vite.config.js` with `root: 'src/main/frontend'`, `base: './'`, Vue plugin, alias `@` to `src/main/frontend/src`, and `build.outDir: '../resources/static'` with `emptyOutDir: true`.

- [ ] **Step 4: Create minimal Vue source**

Create source `index.html`, `main.js`, `App.vue`, style files, and temporary component stubs. The stubs must compile and display `Deep Cover`, `等待室`, `游戏中`, and a hidden toast container.

- [ ] **Step 5: Install dependencies**

Run: `npm install`

Expected: `package-lock.json` is created and npm exits with code 0. If this fails with a registry or DNS error, rerun with escalated network approval.

- [ ] **Step 6: Build and run the static contract**

Run: `npm run build`

Expected: PASS and `src/main/resources/static/index.html` exists.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add package.json package-lock.json vite.config.js src/main/frontend src/main/resources/static src/test/resources/static-frontend-contract.test.mjs
git commit -m "构建：添加 Vue3 前端工程骨架"
```

### Task 2: Domain Labels, Session State, and REST Client

**Files:**
- Create: `src/main/frontend/src/domain/gamePhases.js`
- Create: `src/main/frontend/src/domain/playerColors.js`
- Create: `src/main/frontend/src/domain/labels.js`
- Create: `src/main/frontend/src/composables/useRoomSession.js`
- Create: `src/main/frontend/src/composables/useRoomApi.js`
- Create: `src/main/frontend/src/composables/useClipboard.js`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Extend the failing contract**

Add assertions that `useRoomApi.js` contains `/api/rooms`, `/messages`, `/timer`, `/votes`, `/word/me`, and `/word/descriptions`; `useRoomSession.js` contains `dc_roomCode`, `dc_playerId`, and `dc_playerToken`; `labels.js` contains `CHAT_UNDERCOVER`, `WORD_UNDERCOVER`, `DESCRIBING`, `CHATTING`, `VOTING`, and `ENDED`.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `npm run test:static`

Expected: FAIL with `ENOENT` for `useRoomApi.js`.

- [ ] **Step 3: Add domain modules**

Implement `gamePhases.js` with frozen constants `ROOM_STATUS`, `GAME_MODE`, and `ROOM_EVENT`. Include all existing websocket event types: `CHAT_MESSAGE`, `TIMER_EXPIRED`, `VOTING_STARTED`, `VOTE_UPDATED`, `PLAYER_ELIMINATED`, `ROUND_STARTED`, `WORD_DESCRIPTION_SUBMITTED`, `WORD_ROUND_STARTED`, and `GAME_ENDED`.

Implement `playerColors.js` with the existing color labels and helpers `playerColorValue(color)` and `playerColorLabel(color)`.

Implement `labels.js` with `gameModeLabel`, `roomStatusLabel`, `playerAvatarNumber`, `playerNumberLabel`, `playerTypeLabel`, `winnerLabel`, and `formatISOTime`.

- [ ] **Step 4: Add session, API, and clipboard modules**

Implement `useRoomSession.js` with state fields from the spec, storage keys `dc_roomCode`, `dc_playerId`, `dc_playerToken`, and functions `persist`, `restore`, `setJoinedRoom`, and `clearSession`.

Implement `useRoomApi.js` with a shared `request(method, path, body)` that sends `Content-Type: application/json` and `X-Player-Token` when present. Export API functions `createRoom`, `joinRoom`, `startRoom`, `leaveRoom`, `snapshot`, `messages`, `timer`, `votesSnapshot`, `castVote`, `playerWord`, `wordDescriptions`, and `submitWordDescription`.

Implement `useClipboard.js` with `navigator.clipboard.writeText` and a textarea fallback.

- [ ] **Step 5: Run static contract**

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/frontend/src/domain src/main/frontend/src/composables src/test/resources/static-frontend-contract.test.mjs
git commit -m "功能：迁移前端领域状态和接口客户端"
```

### Task 3: Lobby and Waiting Room Views

**Files:**
- Modify: `src/main/frontend/src/App.vue`
- Modify: `src/main/frontend/src/components/HomeView.vue`
- Modify: `src/main/frontend/src/components/WaitingRoomView.vue`
- Create: `src/main/frontend/src/components/RoomCodeBadge.vue`
- Create: `src/main/frontend/src/components/PlayerList.vue`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Extend the failing contract**

Assert that `HomeView.vue` contains `CHAT_UNDERCOVER`, `WORD_UNDERCOVER`, `createRoom`, and `joinRoom`; `WaitingRoomView.vue` contains `startRoom`, `leaveRoom`, and `RoomCodeBadge`; `PlayerList.vue` contains `playerNumberLabel` and does not contain `AI卧底`.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `npm run test:static`

Expected: FAIL while looking for `createRoom` in `HomeView.vue`.

- [ ] **Step 3: Wire App state and actions**

In `App.vue`, create session and API instances, `currentView`, `toast`, `showToast`, `applySnapshot`, `handleJoined`, `handleCreateRoom`, `handleJoinRoom`, `handleStartRoom`, and `handleLeaveRoom`. Restore existing session on mount and route to `home`, `waiting`, or `game` based on snapshot status.

- [ ] **Step 4: Implement lobby and waiting components**

Implement `HomeView.vue` with selected game mode, room code normalization, create/join loading flags, form errors, and emits `create-room`, `join-room`, and `notify`.

Implement `WaitingRoomView.vue` with `session` prop, room code copy, player list, host-only start button, min-player hint, start loading, and leave loading.

Implement `RoomCodeBadge.vue` with `roomCode` prop and copy feedback.

Implement `PlayerList.vue` with player number, color label, host badge, self badge, and alive/dead state.

- [ ] **Step 5: Build and run static contract**

Run: `npm run build`

Expected: PASS.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/frontend/src/App.vue src/main/frontend/src/components src/test/resources/static-frontend-contract.test.mjs src/main/resources/static
git commit -m "功能：迁移大厅和等待室视图"
```

### Task 4: Game Layout, Timer, Topic, and Player Sidebar

**Files:**
- Modify: `src/main/frontend/src/components/GameView.vue`
- Create: `src/main/frontend/src/components/TimerBadge.vue`
- Create: `src/main/frontend/src/components/TopicPanel.vue`
- Create: `src/main/frontend/src/composables/useGameTimer.js`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Extend the failing contract**

Assert that `GameView.vue` renders `TimerBadge`, `TopicPanel`, and `PlayerList`; `useGameTimer.js` contains `setInterval` and `endsAt`; `TopicPanel.vue` contains `当前话题`.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `npm run test:static`

Expected: FAIL with `ENOENT` for `useGameTimer.js`.

- [ ] **Step 3: Implement timer composable and badge**

Implement `formatTime`, `loadTimer(roomCode)`, `updateTimerDisplay(snapshot)`, `startTimerInterval`, `stopTimerInterval`, and `remainingSeconds`. `TimerBadge.vue` displays `--:--`, warning at 30 seconds, danger at 10 seconds, and an expired state.

- [ ] **Step 4: Implement game shell**

Replace `GameView.vue` with top bar, room code badge, timer badge, phase label, central main area, topic panel, reserved word/vote/chat slots, and player sidebar. Use `roomStatusLabel(session.currentRoomStatus)`.

- [ ] **Step 5: Build and run contract**

Run: `npm run build`

Expected: PASS.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/frontend/src/components/GameView.vue src/main/frontend/src/components/TimerBadge.vue src/main/frontend/src/components/TopicPanel.vue src/main/frontend/src/composables/useGameTimer.js src/test/resources/static-frontend-contract.test.mjs src/main/resources/static
git commit -m "功能：迁移游戏布局和计时状态"
```

### Task 5: Chat Panel and Message Flow

**Files:**
- Create: `src/main/frontend/src/components/ChatPanel.vue`
- Modify: `src/main/frontend/src/components/GameView.vue`
- Modify: `src/main/frontend/src/domain/labels.js`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Extend the failing contract**

Assert that `ChatPanel.vue` contains `chat-avatar`, `chat-msg-body`, `submit`, and `发送`; `labels.js` contains `formatISOTime`.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `npm run test:static`

Expected: FAIL with `ENOENT` for `ChatPanel.vue`.

- [ ] **Step 3: Implement ChatPanel**

Create props `messages`, `players`, `currentPlayerId`, `disabled`, and `placeholder`; emit `send-message`; keep a local `draft`; trim content before submit; render system events centered, self messages with `chat-msg self`, avatars from player color, and timestamps from `formatISOTime`.

- [ ] **Step 4: Wire ChatPanel**

In `GameView.vue`, pass `session.messages`, player data, and disabled state. In `App.vue`, add `handleSendMessage(content)` and show `实时连接建立后即可发送` until socket wiring is added.

- [ ] **Step 5: Build and run contract**

Run: `npm run build`

Expected: PASS.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/frontend/src/components/ChatPanel.vue src/main/frontend/src/components/GameView.vue src/main/frontend/src/App.vue src/main/frontend/src/domain/labels.js src/test/resources/static-frontend-contract.test.mjs src/main/resources/static
git commit -m "功能：迁移聊天面板"
```

### Task 6: Vote and Word Panels

**Files:**
- Create: `src/main/frontend/src/components/VotePanel.vue`
- Create: `src/main/frontend/src/components/WordPanel.vue`
- Modify: `src/main/frontend/src/components/GameView.vue`
- Modify: `src/main/frontend/src/App.vue`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Extend the failing contract**

Assert that `VotePanel.vue` contains `castVote`, `currentPlayerVoted`, and `submittedVoteCount`; `WordPanel.vue` contains `submitWordDescription`, `word-description-item`, and `你的关键词`.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `npm run test:static`

Expected: FAIL with `ENOENT` for `VotePanel.vue`.

- [ ] **Step 3: Implement VotePanel**

Create `session` and `candidates` props; emit `castVote`; compute `hasVoted`; disable candidate buttons when current player is not alive, has voted, or submission is in progress; show submitted/required vote progress; exclude the current player from candidates.

- [ ] **Step 4: Implement WordPanel**

Create `session` prop; emit `submitWordDescription`; show current player number, color, and word; render description list with `word-description-item`; show current turn; show form only for the current describing player; limit draft to 200 characters.

- [ ] **Step 5: Wire panels**

In `GameView.vue`, render `WordPanel` when mode is `WORD_UNDERCOVER` and status is `DESCRIBING`; render `VotePanel` when status is `VOTING`; emit panel actions upward. In `App.vue`, add `handleCastVote`, `handleSubmitWordDescription`, `loadVoteSnapshot`, `loadPlayerWord`, and `loadWordDescriptions`.

- [ ] **Step 6: Build and run contract**

Run: `npm run build`

Expected: PASS.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/frontend/src/components/VotePanel.vue src/main/frontend/src/components/WordPanel.vue src/main/frontend/src/components/GameView.vue src/main/frontend/src/App.vue src/test/resources/static-frontend-contract.test.mjs src/main/resources/static
git commit -m "功能：迁移投票和关键词描述面板"
```

### Task 7: STOMP Socket and Game Flow Reducer

**Files:**
- Create: `src/main/frontend/src/composables/useRoomSocket.js`
- Create: `src/main/frontend/src/composables/useGameFlow.js`
- Modify: `src/main/frontend/src/App.vue`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`

- [ ] **Step 1: Extend the failing contract**

Assert that `useRoomSocket.js` imports `@stomp/stompjs`, connects to `/ws`, and publishes to `/app/rooms/{roomCode}/chat`. Assert that `useGameFlow.js` handles `CHAT_MESSAGE`, `TIMER_EXPIRED`, `VOTING_STARTED`, `VOTE_UPDATED`, `PLAYER_ELIMINATED`, `ROUND_STARTED`, `WORD_DESCRIPTION_SUBMITTED`, `WORD_ROUND_STARTED`, and `GAME_ENDED`.

- [ ] **Step 2: Run the contract to verify it fails**

Run: `npm run test:static`

Expected: FAIL with `ENOENT` for `useRoomSocket.js`.

- [ ] **Step 3: Implement socket composable**

Implement `connect(roomCode)`, `publishChat(roomCode, playerToken, content)`, and `disconnect()`. Use `Client` from `@stomp/stompjs`, broker URL `${wsProtocol}//${window.location.host}/ws`, reconnect delay 3000, heartbeats 10000, subscription `/topic/rooms/{roomCode}/events`, and destination `/app/rooms/{roomCode}/chat`.

- [ ] **Step 4: Implement game flow reducer**

Implement `applySnapshot(snapshot)` and `handleRoomEvent(event)` with event-specific handlers for chat, timer expiry, voting start/update, player elimination, round start, word description submitted, word round start, and game end. Mutate session state and call injected side effects for vote snapshots, word snapshots, timers, room refresh, and system messages.

- [ ] **Step 5: Wire socket and reducer in App**

Create the reducer and socket in `App.vue`; connect after create, join, restore, and snapshot recovery; disconnect on leave and destroyed room; update `handleSendMessage(content)` to call `socket.publishChat(state.roomCode, state.playerToken, content)`.

- [ ] **Step 6: Build and run contract**

Run: `npm run build`

Expected: PASS.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/frontend/src/composables/useRoomSocket.js src/main/frontend/src/composables/useGameFlow.js src/main/frontend/src/App.vue src/test/resources/static-frontend-contract.test.mjs src/main/resources/static
git commit -m "功能：迁移实时事件和游戏流程"
```

### Task 8: Responsive Tactical UI Polish

**Files:**
- Modify: `src/main/frontend/src/styles/base.css`
- Modify: `src/main/frontend/src/styles/components.css`
- Modify: Vue components that need final class names

- [ ] **Step 1: Add layout and interaction styles**

Add complete styles for `.home-shell`, `.home-card`, `.mode-selector`, `.mode-option`, `.room-shell`, `.room-header`, `.room-body`, `.room-footer`, `.game-layout`, `.game-topbar`, `.game-main`, `.game-sidebar`, `.chat-panel`, `.chat-messages`, `.chat-msg`, `.chat-msg.self`, `.chat-input-bar`, `.word-panel`, `.my-word-card`, `.word-description-item`, `.vote-panel`, `.vote-candidate`, `.toast`, and `.toast.error`. Keep panel radius at 8px, tappable targets at 44px or larger, and text contrast high.

- [ ] **Step 2: Verify responsive rules by inspection**

Run: `rg -n "@media|44px|prefers-reduced-motion|chat-input-bar|game-layout" src/main/frontend/src/styles`

Expected: output includes media queries for 900px and 560px, `44px`, `prefers-reduced-motion`, `chat-input-bar`, and `game-layout`.

- [ ] **Step 3: Build**

Run: `npm run build`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/styles src/main/frontend/src/components src/main/resources/static
git commit -m "样式：完善深色战术游戏界面"
```

### Task 9: Spring Static Resource Test and Final Verification

**Files:**
- Modify: `src/test/java/com/cqie/deepcover/web/StaticFrontendTest.java`
- Modify: `src/test/resources/static-frontend-contract.test.mjs`
- Modify: `README.md` if frontend commands need documentation

- [ ] **Step 1: Update Spring static test**

Change `StaticFrontendTest` to expect `Deep Cover`, `/api/rooms`, `type="module"`, and `assets/`. Remove the old `app.js` expectation.

- [ ] **Step 2: Run frontend verification**

Run: `npm run build`

Expected: PASS.

Run: `npm run test:static`

Expected: PASS.

- [ ] **Step 3: Run Maven verification**

Run: `mvn test`

Expected: PASS. If unrelated existing tests fail, record the failing test names and run `mvn -Dtest=StaticFrontendTest test` to isolate static frontend behavior.

- [ ] **Step 4: Check git status**

Run: `git status --short`

Expected: only Vue migration files, generated static assets, updated frontend tests, and optional README changes are modified or untracked by this work. Existing unrelated untracked test files remain untracked and are not staged.

- [ ] **Step 5: Commit**

```bash
git add package.json package-lock.json vite.config.js src/main/frontend src/main/resources/static src/test/resources/static-frontend-contract.test.mjs src/test/java/com/cqie/deepcover/web/StaticFrontendTest.java README.md
git commit -m "测试：验证 Vue3 静态前端产物"
```

## Self-Review

- Spec coverage: Tasks 1-2 create the Vue/Vite build and state/API boundaries; Tasks 3-7 migrate lobby, waiting room, game layout, chat, vote, word, timer, STOMP, and refresh flow; Task 8 applies the `ui-ux-pro-max` dark tactical styling rules; Task 9 verifies Spring static hosting and contract tests.
- Placeholder scan: no unresolved plan markers or vague implementation instructions are present.
- Type consistency: session state fields match the approved design and are reused by API, panels, socket, and game flow tasks. Commit messages are written in Chinese.