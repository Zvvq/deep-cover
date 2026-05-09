const API_BASE = "/api/rooms";
const PLAYER_TOKEN_HEADER = "X-Player-Token";
const SESSION_KEY = "deep-cover-room-session";

const state = {
    roomCode: null,
    playerId: null,
    playerToken: null,
    snapshot: null,
    chat: {
        socket: null,
        roomCode: null,
        connected: false,
        buffer: "",
        subscriptionId: null,
    },
};

const elements = {
    connectionStatus: document.querySelector("#connectionStatus"),
    lobbyPanel: document.querySelector("#lobbyPanel"),
    roomPanel: document.querySelector("#roomPanel"),
    createRoomButton: document.querySelector("#createRoomButton"),
    joinRoomForm: document.querySelector("#joinRoomForm"),
    roomCodeInput: document.querySelector("#roomCodeInput"),
    roomTitle: document.querySelector("#roomTitle"),
    roomSubtitle: document.querySelector("#roomSubtitle"),
    roomStatus: document.querySelector("#roomStatus"),
    playerCount: document.querySelector("#playerCount"),
    myRole: document.querySelector("#myRole"),
    playerList: document.querySelector("#playerList"),
    startRoomButton: document.querySelector("#startRoomButton"),
    leaveRoomButton: document.querySelector("#leaveRoomButton"),
    refreshButton: document.querySelector("#refreshButton"),
    chatStatus: document.querySelector("#chatStatus"),
    chatList: document.querySelector("#chatList"),
    chatForm: document.querySelector("#chatForm"),
    chatInput: document.querySelector("#chatInput"),
    messageArea: document.querySelector("#messageArea"),
};

init();

function init() {
    restoreSession();
    bindEvents();
    render();

    if (state.roomCode) {
        refreshSnapshot();
    }
}

function bindEvents() {
    elements.createRoomButton.addEventListener("click", createRoom);
    elements.joinRoomForm.addEventListener("submit", (event) => {
        event.preventDefault();
        joinRoom(elements.roomCodeInput.value);
    });
    elements.startRoomButton.addEventListener("click", startRoom);
    elements.leaveRoomButton.addEventListener("click", leaveRoom);
    elements.refreshButton.addEventListener("click", refreshSnapshot);
    elements.chatForm.addEventListener("submit", sendChatMessage);

    elements.roomCodeInput.addEventListener("input", () => {
        elements.roomCodeInput.value = elements.roomCodeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, "");
    });
}

async function createRoom() {
    await runAction(async () => {
        const result = await request(API_BASE, { method: "POST" });
        applySession(result);
        showToast(`房间 ${result.roomCode} 创建成功`);
    });
}

async function joinRoom(roomCode) {
    const normalizedCode = roomCode.trim().toUpperCase();
    if (!normalizedCode) {
        showToast("请输入房间号", true);
        return;
    }

    await runAction(async () => {
        const result = await request(`${API_BASE}/${normalizedCode}/join`, { method: "POST" });
        applySession(result);
        showToast(`已加入房间 ${result.roomCode}`);
    });
}

async function startRoom() {
    if (!requireSession()) {
        return;
    }

    await runAction(async () => {
        state.snapshot = await request(`${API_BASE}/${state.roomCode}/start`, {
            method: "POST",
            headers: tokenHeader(),
        });
        persistSession();
        render();
        showToast("房间已开始");
    });
}

async function leaveRoom() {
    if (!requireSession()) {
        return;
    }

    await runAction(async () => {
        const snapshot = await request(`${API_BASE}/${state.roomCode}/leave`, {
            method: "POST",
            headers: tokenHeader(),
        });
        const destroyed = snapshot.status === "DESTROYED";
        clearSession();
        render();
        showToast(destroyed ? "房主已离开，房间已销毁" : "已离开房间");
    });
}

async function refreshSnapshot() {
    if (!state.roomCode) {
        return;
    }

    await runAction(async () => {
        state.snapshot = await request(`${API_BASE}/${state.roomCode}/snapshot`);
        persistSession();
        render();
    });
}

function sendChatMessage(event) {
    event.preventDefault();
    if (!requireSession() || state.snapshot?.status !== "CHATTING") {
        showToast("游戏开始后才能聊天", true);
        return;
    }
    ensureChatConnection();

    const content = elements.chatInput.value.trim();
    if (!content) {
        return;
    }
    if (!state.chat.connected) {
        showToast("聊天连接还未就绪", true);
        return;
    }

    sendStompFrame("SEND", {
        destination: `/app/rooms/${state.roomCode}/chat`,
        "content-type": "application/json",
    }, JSON.stringify({
        playerToken: state.playerToken,
        content,
    }));
    elements.chatInput.value = "";
}

async function request(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            "Accept": "application/json",
            ...(options.headers || {}),
        },
    });

    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    if (!response.ok) {
        throw new Error(body?.message || body?.errorCode || "请求失败");
    }

    return body;
}

async function runAction(action) {
    setButtonsDisabled(true);
    try {
        await action();
    } catch (error) {
        showToast(error.message || "操作失败", true);
    } finally {
        setButtonsDisabled(false);
    }
}

function applySession(result) {
    disconnectChat();
    state.roomCode = result.roomCode;
    state.playerId = result.playerId;
    state.playerToken = result.playerToken;
    state.snapshot = result.snapshot;
    persistSession();
    render();
}

function restoreSession() {
    const rawSession = localStorage.getItem(SESSION_KEY);
    if (!rawSession) {
        return;
    }

    try {
        const saved = JSON.parse(rawSession);
        state.roomCode = saved.roomCode;
        state.playerId = saved.playerId;
        state.playerToken = saved.playerToken;
        state.snapshot = saved.snapshot;
    } catch {
        localStorage.removeItem(SESSION_KEY);
    }
}

function persistSession() {
    localStorage.setItem(SESSION_KEY, JSON.stringify({
        roomCode: state.roomCode,
        playerId: state.playerId,
        playerToken: state.playerToken,
        snapshot: state.snapshot,
    }));
}

function clearSession() {
    disconnectChat();
    state.roomCode = null;
    state.playerId = null;
    state.playerToken = null;
    state.snapshot = null;
    elements.chatList.innerHTML = "";
    localStorage.removeItem(SESSION_KEY);
}

function render() {
    const inRoom = Boolean(state.roomCode && state.snapshot);
    elements.lobbyPanel.hidden = inRoom;
    elements.roomPanel.hidden = !inRoom;
    elements.connectionStatus.textContent = inRoom ? `房间 ${state.roomCode}` : "未进入房间";

    if (!inRoom) {
        updateChatState(false);
        return;
    }

    const players = state.snapshot.players || [];
    const me = players.find((player) => player.id === state.playerId);

    elements.roomTitle.textContent = `房间 ${state.roomCode}`;
    elements.roomSubtitle.textContent = subtitleForStatus(state.snapshot.status);
    elements.roomStatus.textContent = state.snapshot.status;
    elements.playerCount.textContent = String(players.length);
    elements.myRole.textContent = me?.host ? "房主" : "玩家";
    elements.startRoomButton.hidden = !me?.host;
    elements.startRoomButton.disabled = state.snapshot.status !== "WAITING" || players.length < 2;

    elements.playerList.innerHTML = "";
    players.forEach((player, index) => {
        const item = document.createElement("li");
        item.className = "player-card";
        item.innerHTML = `
            <strong>${playerLabel(player, index)}</strong>
            <span>${player.host ? "房主" : "玩家"} · ${player.alive ? "在线" : "已淘汰"}</span>
        `;
        elements.playerList.appendChild(item);
    });

    updateChatState(state.snapshot.status === "CHATTING");
}

function updateChatState(chatting) {
    elements.chatInput.disabled = !chatting;
    elements.chatForm.querySelector("button").disabled = !chatting;
    if (!chatting) {
        elements.chatStatus.textContent = "等待开始";
        disconnectChat();
        return;
    }

    ensureChatConnection();
}

function subtitleForStatus(status) {
    if (status === "WAITING") {
        return "等待玩家加入，至少 2 名真人后房主可以开始";
    }
    if (status === "CHATTING") {
        return "游戏已开始";
    }
    if (status === "DESTROYED") {
        return "房间已销毁";
    }
    return "房间状态已更新";
}

function playerLabel(player, index) {
    if (player.id === state.playerId) {
        return `我 · ${player.host ? "房主" : "玩家"}`;
    }
    return `玩家 ${index + 1}`;
}

function playerLabelById(playerId) {
    const players = state.snapshot?.players || [];
    const index = players.findIndex((player) => player.id === playerId);
    if (playerId === state.playerId) {
        return "我";
    }
    return index >= 0 ? `玩家 ${index + 1}` : "玩家";
}

function tokenHeader() {
    return { [PLAYER_TOKEN_HEADER]: state.playerToken };
}

function requireSession() {
    if (state.roomCode && state.playerToken) {
        return true;
    }
    showToast("当前没有有效房间会话", true);
    return false;
}

function setButtonsDisabled(disabled) {
    [
        elements.createRoomButton,
        elements.startRoomButton,
        elements.leaveRoomButton,
        elements.refreshButton,
        elements.joinRoomForm.querySelector("button"),
    ].forEach((button) => {
        button.disabled = disabled;
    });

    if (state.snapshot?.status === "CHATTING") {
        elements.chatForm.querySelector("button").disabled = disabled;
    }
}

function ensureChatConnection() {
    if (!state.roomCode || state.snapshot?.status !== "CHATTING") {
        return;
    }
    if (state.chat.socket && state.chat.roomCode === state.roomCode) {
        return;
    }

    disconnectChat();
    state.chat.roomCode = state.roomCode;
    state.chat.connected = false;
    state.chat.buffer = "";
    elements.chatStatus.textContent = "连接中";

    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws`);
    state.chat.socket = socket;

    socket.addEventListener("open", () => {
        sendStompFrame("CONNECT", {
            "accept-version": "1.2",
            "heart-beat": "0,0",
        });
    });

    socket.addEventListener("message", (event) => {
        state.chat.buffer += event.data;
        const frames = state.chat.buffer.split("\0");
        state.chat.buffer = frames.pop();
        frames.filter(Boolean).forEach(handleStompFrame);
    });

    socket.addEventListener("close", () => {
        state.chat.connected = false;
        if (state.snapshot?.status === "CHATTING") {
            elements.chatStatus.textContent = "已断开";
        }
    });

    socket.addEventListener("error", () => {
        showToast("聊天连接失败", true);
    });
}

function disconnectChat() {
    if (state.chat.socket) {
        state.chat.socket.close();
    }
    state.chat.socket = null;
    state.chat.roomCode = null;
    state.chat.connected = false;
    state.chat.buffer = "";
    state.chat.subscriptionId = null;
}

function sendStompFrame(command, headers = {}, body = "") {
    if (!state.chat.socket || state.chat.socket.readyState !== WebSocket.OPEN) {
        return;
    }

    const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
    const frame = [command, ...headerLines, "", body].join("\n") + "\0";
    state.chat.socket.send(frame);
}

function handleStompFrame(rawFrame) {
    const normalized = rawFrame.replace(/^\n+/, "");
    const lines = normalized.split("\n");
    const command = lines.shift();
    const blankIndex = lines.indexOf("");
    const body = blankIndex >= 0 ? lines.slice(blankIndex + 1).join("\n") : "";

    if (command === "CONNECTED") {
        state.chat.connected = true;
        state.chat.subscriptionId = `room-events-${state.roomCode}`;
        elements.chatStatus.textContent = "已连接";
        sendStompFrame("SUBSCRIBE", {
            id: state.chat.subscriptionId,
            destination: `/topic/rooms/${state.roomCode}/events`,
        });
        return;
    }

    if (command === "MESSAGE") {
        handleRoomEvent(body);
        return;
    }

    if (command === "ERROR") {
        showToast("聊天消息发送失败", true);
    }
}

function handleRoomEvent(body) {
    if (!body) {
        return;
    }

    const event = JSON.parse(body);
    if (event.type === "CHAT_MESSAGE") {
        appendChatMessage(event.payload);
    }
}

function appendChatMessage(message) {
    const item = document.createElement("li");
    item.className = message.senderPlayerId === state.playerId ? "chat-message mine" : "chat-message";
    item.innerHTML = `
        <div>
            <strong>${playerLabelById(message.senderPlayerId)}</strong>
            <time>${formatTime(message.createdAt)}</time>
        </div>
        <p>${escapeHtml(message.content)}</p>
    `;
    elements.chatList.appendChild(item);
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
}

function formatTime(value) {
    if (!value) {
        return "";
    }
    return new Date(value).toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
    });
}

function escapeHtml(value) {
    const div = document.createElement("div");
    div.textContent = value;
    return div.innerHTML;
}

function showToast(message, error = false) {
    const toast = document.createElement("div");
    toast.className = `toast${error ? " error" : ""}`;
    toast.textContent = message;
    elements.messageArea.appendChild(toast);

    window.setTimeout(() => {
        toast.remove();
    }, 3200);
}
