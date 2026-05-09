const API_BASE = "/api/rooms";
const PLAYER_TOKEN_HEADER = "X-Player-Token";
const SESSION_KEY = "deep-cover-room-session";

const state = {
    roomCode: null,
    playerId: null,
    playerToken: null,
    snapshot: null,
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
        Object.assign(state, JSON.parse(rawSession));
    } catch {
        localStorage.removeItem(SESSION_KEY);
    }
}

function persistSession() {
    localStorage.setItem(SESSION_KEY, JSON.stringify(state));
}

function clearSession() {
    state.roomCode = null;
    state.playerId = null;
    state.playerToken = null;
    state.snapshot = null;
    localStorage.removeItem(SESSION_KEY);
}

function render() {
    const inRoom = Boolean(state.roomCode && state.snapshot);
    elements.lobbyPanel.hidden = inRoom;
    elements.roomPanel.hidden = !inRoom;
    elements.connectionStatus.textContent = inRoom ? `房间 ${state.roomCode}` : "未进入房间";

    if (!inRoom) {
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
}

function subtitleForStatus(status) {
    if (status === "WAITING") {
        return "等待玩家加入，至少 2 名真人后房主可以开始";
    }
    if (status === "CHATTING") {
        return "房间已开始，聊天模块接入后会进入实时对局";
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
