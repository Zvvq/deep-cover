/* ============================================================
   Deep Cover — Frontend Application
   Room creation · Chat (STOMP) · Game Timer
   ============================================================ */

(function () {
  'use strict';

  // ==================== STATE ====================
  const state = {
    roomCode: null,
    playerId: null,
    playerToken: null,
    stompClient: null,
    subscription: null,
    timerInterval: null,
    timerEndsAt: null,
    timerServerOffsetMs: 0,
  };

  function persist() {
    if (state.roomCode) localStorage.setItem('dc_roomCode', state.roomCode);
    else localStorage.removeItem('dc_roomCode');
    if (state.playerId) localStorage.setItem('dc_playerId', state.playerId);
    else localStorage.removeItem('dc_playerId');
    if (state.playerToken) localStorage.setItem('dc_playerToken', state.playerToken);
    else localStorage.removeItem('dc_playerToken');
  }

  function restore() {
    state.roomCode = localStorage.getItem('dc_roomCode') || null;
    state.playerId = localStorage.getItem('dc_playerId') || null;
    state.playerToken = localStorage.getItem('dc_playerToken') || null;
  }

  function clearState() {
    state.roomCode = null;
    state.playerId = null;
    state.playerToken = null;
    persist();
  }

  // ==================== DOM REFS ====================
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => document.querySelectorAll(sel);

  // Views
  const homeView = $('#home-view');
  const waitingView = $('#waiting-view');
  const gameView = $('#game-view');

  // Home
  const btnCreate = $('#btn-create');
  const btnJoin = $('#btn-join');
  const roomCodeInput = $('#room-code-input');
  const joinForm = $('#join-form');
  const joinError = $('#join-error');
  const homeStatus = $('#home-status');

  // Waiting
  const btnLeaveWaiting = $('#btn-leave-waiting');
  const waitingRoomCode = $('#waiting-room-code');
  const waitingRoomStatus = $('#waiting-room-status');
  const waitingPlayerCount = $('#waiting-player-count');
  const waitingPlayerList = $('#waiting-player-list');
  const waitingHint = $('#waiting-hint');
  const btnStartGame = $('#btn-start-game');

  // Game
  const gameRoomCode = $('#game-room-code');
  const timerValue = $('#timer-value');
  const timerDisplay = $('#timer-display');
  const gamePhaseBadge = $('#game-phase-badge');
  const chatMessages = $('#chat-messages');
  const chatForm = $('#chat-form');
  const chatInput = $('#chat-input');
  const btnSend = $('#btn-send');
  const gamePlayerCount = $('#game-player-count');
  const gamePlayerList = $('#game-player-list');

  // ==================== HELPERS ====================
  function formatTime(seconds) {
    if (seconds <= 0) return '00:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
  }

  function formatISOTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }

  function playerLabel(player) {
    var typeName = player.type === 'AI' ? 'AI卧底' : '玩家';
    let label = typeName + ' ' + (player.id || '').substring(0, 4);
    if (player.host) label += ' (房主)';
    return label;
  }

  function isHost(playerId) {
    return state.playerId === playerId;
  }

  // ==================== TOAST ====================
  let toastTimer = null;
  function toast(msg, isError) {
    const old = $('.toast');
    if (old) old.remove();
    if (toastTimer) clearTimeout(toastTimer);

    const el = document.createElement('div');
    el.className = 'toast' + (isError ? ' error' : '');
    el.textContent = msg;
    document.body.appendChild(el);

    toastTimer = setTimeout(function () {
      el.remove();
      toastTimer = null;
    }, 3500);
  }

  // ==================== API ====================
  const API = {
    async request(method, path, body) {
      const headers = { 'Content-Type': 'application/json' };
      if (state.playerToken) headers['X-Player-Token'] = state.playerToken;

      const options = { method, headers };
      if (body) options.body = JSON.stringify(body);

      let res;
      try {
        res = await fetch(path, options);
      } catch (e) {
        throw new Error('网络连接失败，请检查网络');
      }

      if (!res.ok) {
        let errData;
        try { errData = await res.json(); } catch (_) { /* ignore */ }
        const msg = (errData && errData.message) ? errData.message : ('请求失败 (' + res.status + ')');
        const err = new Error(msg);
        err.code = errData && errData.errorCode;
        throw err;
      }

      if (res.status === 204) return null;
      return res.json();
    },

    createRoom() {
      return API.request('POST', '/api/rooms');
    },

    joinRoom(roomCode) {
      return API.request('POST', '/api/rooms/' + encodeURIComponent(roomCode) + '/join');
    },

    startRoom() {
      return API.request('POST', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/start');
    },

    leaveRoom() {
      return API.request('POST', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/leave');
    },

    snapshot() {
      return API.request('GET', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/snapshot');
    },

    messages() {
      return API.request('GET', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/messages');
    },

    timer() {
      return API.request('GET', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/timer');
    },
  };

  // ==================== VIEW SWITCHING ====================
  function showView(view) {
    [homeView, waitingView, gameView].forEach(function (v) {
      v.hidden = true;
    });
    view.hidden = false;
  }

  // ==================== HOME ====================
  function initHome() {
    btnCreate.disabled = false;
    btnCreate.addEventListener('click', handleCreate);
    joinForm.addEventListener('submit', handleJoin);
    roomCodeInput.addEventListener('input', function () {
      var val = roomCodeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
      roomCodeInput.value = val;
      btnJoin.disabled = val.length !== 6;
      joinError.hidden = true;
    });
  }

  async function handleCreate(e) {
    e.preventDefault();
    btnCreate.disabled = true;
    homeStatus.hidden = false;
    homeStatus.textContent = '正在创建房间…';

    try {
      var data = await API.createRoom();
      onRoomJoined(data);
    } catch (err) {
      homeStatus.textContent = '';
      homeStatus.hidden = true;
      btnCreate.disabled = false;
      toast(err.message, true);
    }
  }

  async function handleJoin(e) {
    e.preventDefault();
    var code = roomCodeInput.value.trim().toUpperCase();
    if (code.length !== 6) return;

    btnJoin.disabled = true;
    joinError.hidden = true;

    try {
      var data = await API.joinRoom(code);
      onRoomJoined(data);
    } catch (err) {
      joinError.textContent = err.message;
      joinError.hidden = false;
      btnJoin.disabled = false;
    }
  }

  function onRoomJoined(data) {
    state.roomCode = data.roomCode;
    state.playerId = data.playerId;
    state.playerToken = data.playerToken;
    persist();
    homeStatus.hidden = true;
    homeStatus.textContent = '';
    btnCreate.disabled = false;
    btnJoin.disabled = false;
    roomCodeInput.value = '';
    connectWebSocket(data.roomCode);
    renderWaiting(data.snapshot);
    showView(waitingView);
  }

  // ==================== WAITING ROOM ====================
  function initWaiting() {
    btnLeaveWaiting.addEventListener('click', handleLeaveWaiting);
    btnStartGame.addEventListener('click', handleStartGame);
  }

  function renderWaiting(snapshot) {
    waitingRoomCode.textContent = snapshot.roomCode;
    waitingRoomStatus.textContent = snapshot.status === 'WAITING' ? '等待中' : snapshot.status;
    waitingRoomStatus.classList.toggle('live', snapshot.status === 'WAITING');

    var players = snapshot.players || [];
    waitingPlayerCount.textContent = '(' + players.length + '/8)';

    waitingPlayerList.innerHTML = '';
    players.forEach(function (p) {
      var li = document.createElement('li');
      li.className = 'player-item';

      var dot = document.createElement('span');
      dot.className = 'player-dot';
      dot.style.backgroundColor = p.type === 'AI' ? '#F59E0B' : (isHost(p.id) ? '#22C55E' : '#64748B');

      var name = document.createElement('span');
      name.className = 'player-name';
      name.textContent = playerLabel(p);

      li.appendChild(dot);
      li.appendChild(name);

      if (p.host) {
        var badge = document.createElement('span');
        badge.className = 'player-badge';
        badge.textContent = '房主';
        li.appendChild(badge);
      }
      if (isHost(p.id)) {
        var youBadge = document.createElement('span');
        youBadge.className = 'player-badge you';
        youBadge.textContent = '你';
        li.appendChild(youBadge);
      }

      waitingPlayerList.appendChild(li);
    });

    // Show start button only for host when >= 2 players
    var amHost = players.some(function (p) { return p.host && isHost(p.id); });
    var canStart = amHost && players.length >= 2;
    btnStartGame.hidden = !canStart;
    waitingHint.hidden = !amHost || players.length >= 2;
  }

  async function handleLeaveWaiting() {
    try {
      await API.leaveRoom();
    } catch (_) { /* ignore — room may already be gone */ }
    disconnectWebSocket();
    clearState();
    showView(homeView);
  }

  async function handleStartGame() {
    btnStartGame.disabled = true;
    try {
      var snapshot = await API.startRoom();
      btnStartGame.disabled = false;
      // Game started — snapshot has CHATTING status
      enterGame(snapshot);
    } catch (err) {
      btnStartGame.disabled = false;
      toast(err.message, true);
    }
  }

  // ==================== GAME VIEW ====================
  function initGame() {
    chatForm.addEventListener('submit', handleSendChat);
    chatInput.addEventListener('input', function () {
      btnSend.disabled = chatInput.value.trim().length === 0;
    });
  }

  function enterGame(snapshot) {
    showView(gameView);
    gameRoomCode.textContent = snapshot.roomCode;
    renderGamePlayers(snapshot.players || []);

    // Load chat history
    loadChatHistory();

    // Load timer
    loadTimer();

    // Clear chat input
    chatInput.value = '';
    btnSend.disabled = true;
  }

  function renderGamePlayers(players) {
    gamePlayerCount.textContent = '(' + players.length + ')';
    gamePlayerList.innerHTML = '';
    players.forEach(function (p) {
      var li = document.createElement('li');
      li.className = 'player-item';

      var dot = document.createElement('span');
      dot.className = 'player-dot';
      dot.style.backgroundColor = p.alive ? (p.type === 'AI' ? '#F59E0B' : (isHost(p.id) ? '#22C55E' : '#64748B')) : '#DC2626';

      var name = document.createElement('span');
      name.className = 'player-name';
      name.textContent = playerLabel(p);

      var status = document.createElement('span');
      status.className = 'player-status-icon ' + (p.alive ? 'alive' : 'dead');
      status.textContent = p.alive ? '存活' : '出局';

      li.appendChild(dot);
      li.appendChild(name);
      li.appendChild(status);

      gamePlayerList.appendChild(li);
    });
  }

  async function loadChatHistory() {
    try {
      var messages = await API.messages();
      chatMessages.innerHTML = '';
      if (!messages || messages.length === 0) {
        chatMessages.innerHTML = '<p class="chat-placeholder">暂无消息，开始讨论吧</p>';
      } else {
        messages.forEach(renderChatMessage);
      }
      scrollChat();
    } catch (err) {
      chatMessages.innerHTML = '<p class="chat-placeholder">加载消息失败</p>';
    }
  }

  function renderChatMessage(msg) {
    // Remove placeholder if present
    var placeholder = chatMessages.querySelector('.chat-placeholder');
    if (placeholder) placeholder.remove();

    var div = document.createElement('div');
    var isMine = msg.senderPlayerId === state.playerId;
    div.className = 'chat-msg' + (isMine ? ' self' : '');

    var sender = document.createElement('div');
    sender.className = 'chat-msg-sender';
    sender.textContent = isMine ? '你' : ('玩家 ' + (msg.senderPlayerId || '').substring(0, 4));

    var content = document.createElement('div');
    content.className = 'chat-msg-content';
    content.textContent = msg.content;

    var time = document.createElement('div');
    time.className = 'chat-msg-time';
    time.textContent = formatISOTime(msg.createdAt);

    div.appendChild(sender);
    div.appendChild(content);
    div.appendChild(time);
    chatMessages.appendChild(div);
  }

  function addEventMessage(text) {
    var placeholder = chatMessages.querySelector('.chat-placeholder');
    if (placeholder) placeholder.remove();

    var div = document.createElement('div');
    div.className = 'chat-msg event';
    div.textContent = text;
    chatMessages.appendChild(div);
    scrollChat();
  }

  function scrollChat() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
  }

  async function handleSendChat(e) {
    e.preventDefault();
    var content = chatInput.value.trim();
    if (!content) return;

    // Optimistic: send via WebSocket STOMP
    if (state.stompClient && state.stompClient.connected) {
      state.stompClient.publish({
        destination: '/app/rooms/' + encodeURIComponent(state.roomCode) + '/chat',
        body: JSON.stringify({ playerToken: state.playerToken, content: content }),
      });
      chatInput.value = '';
      btnSend.disabled = true;
      chatInput.focus();
    } else {
      toast('连接已断开', true);
    }
  }

  // ==================== TIMER ====================
  async function loadTimer() {
    try {
      var data = await API.timer();
      updateTimerDisplay(data);
    } catch (err) {
      // Timer may not exist yet
      timerValue.textContent = '--:--';
      gamePhaseBadge.textContent = '';
      gamePhaseBadge.className = 'phase-badge';
    }
  }

  function updateTimerDisplay(data) {
    if (!data) return;

    gamePhaseBadge.textContent = data.phase === 'CHATTING' ? '讨论阶段' : (data.phase === 'VOTING' ? '投票阶段' : data.phase);
    gamePhaseBadge.className = 'phase-badge' + (data.status === 'EXPIRED' ? ' expired' : '');

    if (data.status === 'EXPIRED') {
      timerValue.textContent = '00:00';
      timerDisplay.classList.add('danger');
      timerDisplay.classList.remove('warning');
      stopTimerInterval();
      return;
    }

    if (data.endsAt) {
      state.timerEndsAt = new Date(data.endsAt).getTime();
      if (data.serverNow) {
        state.timerServerOffsetMs = new Date(data.serverNow).getTime() - Date.now();
      } else if (data.remainingSeconds != null) {
        state.timerServerOffsetMs = state.timerEndsAt - Date.now() - data.remainingSeconds * 1000;
      }
      startTimerInterval();
    }
  }

  function startTimerInterval() {
    stopTimerInterval();
    var tick = function () {
      if (!state.timerEndsAt) return;
      var serverAdjustedNow = Date.now() + state.timerServerOffsetMs;
      var remaining = Math.max(0, Math.ceil((state.timerEndsAt - serverAdjustedNow) / 1000));
      timerValue.textContent = formatTime(remaining);

      timerDisplay.classList.remove('warning', 'danger');
      if (remaining <= 30) timerDisplay.classList.add('danger');
      else if (remaining <= 60) timerDisplay.classList.add('warning');

      if (remaining <= 0) {
        stopTimerInterval();
        gamePhaseBadge.textContent = '已结束';
        gamePhaseBadge.className = 'phase-badge expired';
      }
    };
    tick();
    state.timerInterval = setInterval(tick, 250);
  }

  function stopTimerInterval() {
    if (state.timerInterval) {
      clearInterval(state.timerInterval);
      state.timerInterval = null;
    }
  }

  // ==================== WEBSOCKET (STOMP) ====================
  function connectWebSocket(roomCode) {
    disconnectWebSocket();

    var wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
    var client = new StompJs.Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: function (_) { /* silent */ },
    });

    client.onConnect = function () {
      var sub = client.subscribe('/topic/rooms/' + encodeURIComponent(roomCode) + '/events', function (message) {
        handleWebSocketEvent(message);
      });
      state.subscription = sub;
    };

    client.onStompError = function (frame) {
      console.error('STOMP error:', frame.headers['message']);
    };

    client.onWebSocketClose = function () {
      // Reconnection handled by library
    };

    client.activate();
    state.stompClient = client;
  }

  function disconnectWebSocket() {
    if (state.subscription) {
      try { state.subscription.unsubscribe(); } catch (_) { /* ignore */ }
      state.subscription = null;
    }
    if (state.stompClient) {
      try { state.stompClient.deactivate(); } catch (_) { /* ignore */ }
      state.stompClient = null;
    }
    stopTimerInterval();
  }

  function handleWebSocketEvent(message) {
    try {
      var event = JSON.parse(message.body);
    } catch (_) {
      return;
    }

    switch (event.type) {
      case 'CHAT_MESSAGE':
        handleChatEvent(event.payload);
        break;
      case 'TIMER_EXPIRED':
        handleTimerExpired(event.payload);
        break;
      default:
        // Unknown event — ignore
    }
  }

  function handleChatEvent(payload) {
    if (!payload) return;
    renderChatMessage(payload);
    scrollChat();
  }

  function handleTimerExpired(payload) {
    addEventMessage('计时器已到期 — ' + (payload.phase === 'CHATTING' ? '讨论时间结束' : '投票时间结束'));
    stopTimerInterval();
    timerValue.textContent = '00:00';
    timerDisplay.classList.add('danger');
    gamePhaseBadge.textContent = '已结束';
    gamePhaseBadge.className = 'phase-badge expired';
  }

  // ==================== POLLING (Waiting Room) ====================
  // Poll for room state changes while in waiting room
  var waitingPollTimer = null;

  function startWaitingPoll() {
    stopWaitingPoll();
    waitingPollTimer = setInterval(async function () {
      if (waitingView.hidden) { stopWaitingPoll(); return; }
      try {
        var snapshot = await API.snapshot();
        if (snapshot.status !== 'WAITING') {
          // Game started or room ended
          stopWaitingPoll();
          if (snapshot.status === 'CHATTING' || snapshot.status === 'VOTING') {
            enterGame(snapshot);
          } else if (snapshot.status === 'ENDED' || snapshot.status === 'DESTROYED') {
            toast('房间已结束', false);
            disconnectWebSocket();
            clearState();
            showView(homeView);
          }
          return;
        }
        renderWaiting(snapshot);
      } catch (err) {
        if (err.code === 'ROOM_NOT_FOUND') {
          stopWaitingPoll();
          disconnectWebSocket();
          clearState();
          toast('房间已解散', false);
          showView(homeView);
        }
        // Other errors — ignore, will retry next poll
      }
    }, 3000);
  }

  function stopWaitingPoll() {
    if (waitingPollTimer) {
      clearInterval(waitingPollTimer);
      waitingPollTimer = null;
    }
  }

  // ==================== INIT ====================
  function init() {
    initHome();
    initWaiting();
    initGame();

    // Attempt to restore session
    restore();
    if (state.roomCode && state.playerToken) {
      // Reconnect to existing room
      connectWebSocket(state.roomCode);
      API.snapshot().then(function (snapshot) {
        if (snapshot.status === 'WAITING') {
          renderWaiting(snapshot);
          showView(waitingView);
          startWaitingPoll();
        } else if (snapshot.status === 'CHATTING' || snapshot.status === 'VOTING') {
          showView(gameView);
          enterGame(snapshot);
        } else {
          // ENDED, DESTROYED — go home
          disconnectWebSocket();
          clearState();
          showView(homeView);
        }
      }).catch(function () {
        disconnectWebSocket();
        clearState();
        showView(homeView);
      });
    } else {
      showView(homeView);
    }

    // Listen for waiting view becoming active
    var waitingObserver = new MutationObserver(function () {
      if (!waitingView.hidden) {
        startWaitingPoll();
      } else {
        stopWaitingPoll();
      }
    });
    waitingObserver.observe(waitingView, { attributes: true, attributeFilter: ['hidden'] });
  }

  // Kick off
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
