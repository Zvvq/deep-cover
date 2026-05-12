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
    timerPhase: null,
    timerServerOffsetMs: 0,
    currentRoomStatus: null,
    currentPlayers: [],
    voteSnapshot: null,
    voteCandidateIds: [],
    voteSubmitting: false,
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
    state.currentRoomStatus = null;
    state.currentPlayers = [];
    state.voteSnapshot = null;
    state.voteCandidateIds = [];
    state.voteSubmitting = false;
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
  const btnCopyWaitingRoom = $('#btn-copy-waiting-room');
  const waitingRoomCode = $('#waiting-room-code');
  const waitingRoomStatus = $('#waiting-room-status');
  const waitingPlayerCount = $('#waiting-player-count');
  const waitingPlayerList = $('#waiting-player-list');
  const waitingHint = $('#waiting-hint');
  const btnStartGame = $('#btn-start-game');

  // Game
  const gameRoomCode = $('#game-room-code');
  const btnCopyGameRoom = $('#btn-copy-game-room');
  const timerValue = $('#timer-value');
  const timerDisplay = $('#timer-display');
  const gamePhaseBadge = $('#game-phase-badge');
  const votePanel = $('#vote-panel');
  const voteProgress = $('#vote-progress');
  const voteStatus = $('#vote-status');
  const voteCandidates = $('#vote-candidates');
  const voteHint = $('#vote-hint');
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

  function currentPlayer() {
    return state.currentPlayers.find(function (player) {
      return player.id === state.playerId;
    }) || null;
  }

  function isCurrentPlayerAlive() {
    var player = currentPlayer();
    return !player || player.alive !== false;
  }

  function playerNameById(playerId) {
    var player = state.currentPlayers.find(function (p) { return p.id === playerId; });
    if (player) return playerLabel(player);
    return '玩家 ' + (playerId || '').substring(0, 4);
  }

  function winnerLabel(winner) {
    if (winner === 'HUMAN') return '真人阵营';
    if (winner === 'AI') return 'AI 阵营';
    return winner || '未知阵营';
  }

  function setChatInputState(enabled, placeholder) {
    chatInput.disabled = !enabled;
    chatInput.placeholder = placeholder;
    btnSend.disabled = !enabled || chatInput.value.trim().length === 0;
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

  async function copyText(text) {
    if (!text) return false;

    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch (_) {
        // Fall back to the textarea path below.
      }
    }

    var textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();

    var ok = false;
    try {
      ok = document.execCommand('copy');
    } finally {
      textarea.remove();
    }
    return ok;
  }

  async function handleCopyRoomCode() {
    var code = state.roomCode || waitingRoomCode.textContent || gameRoomCode.textContent;
    try {
      var copied = await copyText(code);
      toast(copied ? '房间号已复制' : '复制失败，请手动复制', !copied);
    } catch (_) {
      toast('复制失败，请手动复制', true);
    }
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

    votesSnapshot() {
      return API.request('GET', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/votes');
    },

    castVote(targetPlayerId) {
      return API.request('POST', '/api/rooms/' + encodeURIComponent(state.roomCode) + '/votes', {
        targetPlayerId: targetPlayerId,
      });
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
    btnCopyWaitingRoom.addEventListener('click', handleCopyRoomCode);
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
    btnCopyGameRoom.addEventListener('click', handleCopyRoomCode);
    chatForm.addEventListener('submit', handleSendChat);
    chatInput.addEventListener('input', function () {
      btnSend.disabled = chatInput.disabled || chatInput.value.trim().length === 0;
    });
    voteCandidates.addEventListener('click', function (event) {
      var button = event.target.closest('[data-target-player-id]');
      if (!button || button.disabled) return;
      handleVoteSubmit(button.dataset.targetPlayerId);
    });
  }

  function enterGame(snapshot) {
    showView(gameView);
    applyGameSnapshot(snapshot);

    // Clear chat input
    chatInput.value = '';
    btnSend.disabled = chatInput.disabled || chatInput.value.trim().length === 0;
  }

  function applyGameSnapshot(snapshot) {
    if (!snapshot) return;

    var previousStatus = state.currentRoomStatus;
    state.currentRoomStatus = snapshot.status;
    state.roomCode = snapshot.roomCode || state.roomCode;
    gameRoomCode.textContent = snapshot.roomCode;
    renderGamePlayers(snapshot.players || []);

    if (snapshot.status === 'VOTING') {
      if (previousStatus !== 'CHATTING') {
        chatMessages.innerHTML = '<p class="chat-placeholder">投票阶段，聊天已暂停</p>';
      }
      enterVoting();
    } else if (snapshot.status === 'CHATTING') {
      enterChatting();
      if (previousStatus !== 'CHATTING') loadChatHistory();
    } else if (snapshot.status === 'ENDED') {
      enterEnded();
    }
  }

  async function refreshRoomState() {
    try {
      var snapshot = await API.snapshot();
      if (snapshot.status === 'WAITING') {
        renderWaiting(snapshot);
        showView(waitingView);
      } else if (snapshot.status === 'CHATTING' || snapshot.status === 'VOTING' || snapshot.status === 'ENDED') {
        showView(gameView);
        applyGameSnapshot(snapshot);
      } else if (snapshot.status === 'DESTROYED') {
        toast('房间已解散', false);
        disconnectWebSocket();
        clearState();
        showView(homeView);
      }
    } catch (err) {
      if (err.code === 'ROOM_NOT_FOUND') {
        disconnectWebSocket();
        clearState();
        toast('房间已解散', false);
        showView(homeView);
      }
    }
  }

  function scheduleRoomRefresh(delayMs) {
    setTimeout(refreshRoomState, delayMs);
  }

  function enterChatting() {
    hideVotePanel();
    state.voteSnapshot = null;
    state.voteCandidateIds = [];
    gamePhaseBadge.textContent = '讨论阶段';
    gamePhaseBadge.className = 'phase-badge';
    setChatInputState(isCurrentPlayerAlive(), isCurrentPlayerAlive() ? '输入消息…' : '你已出局，无法发言');
    loadTimer();
  }

  function enterVoting() {
    gamePhaseBadge.textContent = '投票阶段';
    gamePhaseBadge.className = 'phase-badge';
    setChatInputState(false, '投票阶段暂停发言');
    votePanel.hidden = false;
    renderVotePanel();
    loadVoteSnapshot();
    loadTimer();
  }

  function enterEnded() {
    hideVotePanel();
    stopTimerInterval();
    timerValue.textContent = '--:--';
    timerDisplay.classList.remove('warning', 'danger');
    gamePhaseBadge.textContent = '游戏结束';
    gamePhaseBadge.className = 'phase-badge expired';
    setChatInputState(false, '游戏已结束');
  }

  function hideVotePanel() {
    votePanel.hidden = true;
    voteCandidates.innerHTML = '';
    voteHint.textContent = '';
  }

  function renderGamePlayers(players) {
    state.currentPlayers = players;
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

  async function loadVoteSnapshot() {
    if (state.currentRoomStatus !== 'VOTING') return;
    try {
      state.voteSnapshot = await API.votesSnapshot();
      renderVotePanel();
    } catch (err) {
      voteProgress.textContent = '投票状态暂不可用';
      voteHint.textContent = err.message || '请稍后重试';
    }
  }

  function renderVotePanel() {
    if (state.currentRoomStatus !== 'VOTING') {
      hideVotePanel();
      return;
    }

    votePanel.hidden = false;
    var snapshot = state.voteSnapshot;
    var hasVoted = !!(snapshot && snapshot.currentPlayerVoted);
    var alive = isCurrentPlayerAlive();

    voteStatus.textContent = hasVoted ? '已提交' : (state.voteSubmitting ? '提交中' : '待投票');
    voteStatus.className = 'vote-status-pill' + (hasVoted ? ' done' : '');
    voteProgress.textContent = snapshot
      ? ('已提交 ' + snapshot.submittedVoteCount + '/' + snapshot.requiredVoteCount + ' 票')
      : '正在加载投票状态';

    var candidates = voteCandidatesForCurrentPlayer();
    voteCandidates.innerHTML = '';
    candidates.forEach(function (player) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'vote-candidate';
      button.dataset.targetPlayerId = player.id;
      button.disabled = !alive || hasVoted || state.voteSubmitting;

      var name = document.createElement('span');
      name.className = 'vote-candidate-name';
      name.textContent = playerLabel(player);

      var meta = document.createElement('span');
      meta.className = 'vote-candidate-meta';
      meta.textContent = player.id.substring(0, 8);

      button.appendChild(name);
      button.appendChild(meta);
      voteCandidates.appendChild(button);
    });

    if (!alive) {
      voteHint.textContent = '你已出局，不能参与本轮投票';
    } else if (hasVoted) {
      voteHint.textContent = '投票已提交，等待其他玩家完成投票';
    } else if (candidates.length === 0) {
      voteHint.textContent = '暂无可投票对象';
    } else {
      voteHint.textContent = '选择你认为最可疑的玩家';
    }
  }

  function voteCandidatesForCurrentPlayer() {
    var candidateIds = state.voteCandidateIds.length
      ? state.voteCandidateIds
      : state.currentPlayers.filter(function (player) { return player.alive; }).map(function (player) { return player.id; });

    return candidateIds
      .map(function (playerId) {
        return state.currentPlayers.find(function (player) { return player.id === playerId; });
      })
      .filter(function (player) {
        return player && player.alive && player.id !== state.playerId;
      });
  }

  async function handleVoteSubmit(targetPlayerId) {
    if (!targetPlayerId || state.voteSubmitting) return;

    state.voteSubmitting = true;
    var settled = false;
    renderVotePanel();
    try {
      var result = await API.castVote(targetPlayerId);
      toast('投票已提交', false);
      await loadVoteSnapshot();
      if (result && result.settled) {
        settled = true;
        setVotingLocked('投票已结束，等待结算');
        scheduleRoomRefresh(700);
      }
    } catch (err) {
      toast(err.message, true);
      await loadVoteSnapshot();
    } finally {
      state.voteSubmitting = false;
      if (!settled) renderVotePanel();
    }
  }

  function setVotingLocked(message) {
    voteStatus.textContent = '结算中';
    voteStatus.className = 'vote-status-pill done';
    voteHint.textContent = message;
    voteCandidates.querySelectorAll('button').forEach(function (button) {
      button.disabled = true;
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
    if (chatInput.disabled || state.currentRoomStatus !== 'CHATTING') return;
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
      stopTimerInterval();
      state.timerEndsAt = null;
      state.timerPhase = null;
      timerValue.textContent = '--:--';
      timerDisplay.classList.remove('warning', 'danger');
      gamePhaseBadge.textContent = state.currentRoomStatus === 'VOTING'
        ? '投票阶段'
        : (state.currentRoomStatus === 'CHATTING' ? '讨论阶段' : '');
      gamePhaseBadge.className = 'phase-badge';
    }
  }

  function updateTimerDisplay(data) {
    if (!data) return;

    state.timerPhase = data.phase;
    gamePhaseBadge.textContent = data.phase === 'CHATTING' ? '讨论阶段' : (data.phase === 'VOTING' ? '投票阶段' : data.phase);
    gamePhaseBadge.className = 'phase-badge' + (data.status === 'EXPIRED' ? ' expired' : '');

    if (data.status === 'EXPIRED') {
      setTimerExpiredUi(data.phase);
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
        setTimerExpiredUi(state.timerPhase);
        scheduleRoomRefresh(700);
      }
    };
    tick();
    state.timerInterval = setInterval(tick, 250);
  }

  function setTimerExpiredUi(phase) {
    timerValue.textContent = '00:00';
    timerDisplay.classList.add('danger');
    timerDisplay.classList.remove('warning');
    gamePhaseBadge.textContent = phase === 'CHATTING'
      ? '等待投票'
      : (phase === 'VOTING' ? '等待结算' : '已结束');
    gamePhaseBadge.className = 'phase-badge expired';
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
    state.timerEndsAt = null;
    state.timerPhase = null;
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
      case 'VOTING_STARTED':
        handleVotingStarted(event.payload);
        break;
      case 'VOTE_UPDATED':
        handleVoteUpdated(event.payload);
        break;
      case 'PLAYER_ELIMINATED':
        handlePlayerEliminated(event.payload);
        break;
      case 'ROUND_STARTED':
        handleRoundStarted(event.payload);
        break;
      case 'GAME_ENDED':
        handleGameEnded(event.payload);
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
    if (!payload) return;
    addEventMessage('计时器已到期 — ' + (payload.phase === 'CHATTING' ? '讨论时间结束' : '投票时间结束'));
    stopTimerInterval();
    setTimerExpiredUi(payload.phase);

    if (payload.phase === 'CHATTING') {
      setChatInputState(false, '正在进入投票阶段');
      scheduleRoomRefresh(500);
      scheduleRoomRefresh(1400);
    } else if (payload.phase === 'VOTING') {
      setVotingLocked('投票时间结束，等待结算');
      scheduleRoomRefresh(900);
    }
  }

  function handleVotingStarted(payload) {
    if (!payload) return;
    state.currentRoomStatus = 'VOTING';
    state.voteSnapshot = null;
    state.voteCandidateIds = payload.candidatePlayerIds || [];
    addEventMessage('进入第 ' + payload.roundNumber + ' 轮投票');
    enterVoting();
    refreshRoomState();
  }

  function handleVoteUpdated(payload) {
    if (!payload) return;
    if (!state.voteSnapshot || state.voteSnapshot.roundNumber !== payload.roundNumber) {
      loadVoteSnapshot();
      return;
    }
    state.voteSnapshot = {
      roomCode: payload.roomCode,
      roundNumber: payload.roundNumber,
      requiredVoteCount: payload.requiredVoteCount,
      submittedVoteCount: payload.submittedVoteCount,
      currentPlayerVoted: state.voteSnapshot.currentPlayerVoted,
    };
    renderVotePanel();
  }

  function handlePlayerEliminated(payload) {
    if (!payload) return;
    var eliminatedName = playerNameById(payload.playerId);
    addEventMessage('投票结算 — ' + eliminatedName + ' 已出局');
    state.currentPlayers = state.currentPlayers.map(function (player) {
      if (player.id !== payload.playerId) return player;
      return Object.assign({}, player, { alive: false });
    });
    renderGamePlayers(state.currentPlayers);
    hideVotePanel();
    setChatInputState(false, '等待下一轮');
    gamePhaseBadge.textContent = '结算中';
    gamePhaseBadge.className = 'phase-badge expired';
  }

  function handleRoundStarted(payload) {
    if (!payload) return;
    addEventMessage('第 ' + payload.roundNumber + ' 轮讨论开始');
    state.voteSnapshot = null;
    state.voteCandidateIds = [];
    state.currentRoomStatus = 'CHATTING';
    refreshRoomState();
  }

  function handleGameEnded(payload) {
    if (!payload) return;
    addEventMessage('游戏结束 — ' + winnerLabel(payload.winner) + '胜利');
    state.currentRoomStatus = 'ENDED';
    enterEnded();
    scheduleRoomRefresh(500);
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
