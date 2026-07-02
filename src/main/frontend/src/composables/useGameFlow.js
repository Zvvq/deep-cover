import { GAME_MODE, ROOM_EVENT, ROOM_STATUS, isGameActive } from '../domain/gamePhases.js';
import { playerNumberLabel, winnerLabel } from '../domain/labels.js';

export function useGameFlow(state, { api, timer, setView, showToast }) {
  function currentPlayer() {
    return state.currentPlayers.find((player) => player.id === state.playerId) || null;
  }

  function isCurrentPlayerAlive() {
    const player = currentPlayer();
    return !player || player.alive !== false;
  }

  function addSystemMessage(content) {
    state.messages.push({
      id: 'system-' + Date.now() + '-' + Math.random().toString(16).slice(2),
      system: true,
      content,
      createdAt: new Date().toISOString(),
    });
  }

  async function loadChatHistory() {
    if (!state.roomCode) return;
    try {
      state.messages = await api.messages(state.roomCode);
    } catch {
      state.messages = [];
      addSystemMessage('聊天记录加载失败');
    }
  }

  async function loadVoteSnapshot() {
    if (!state.roomCode || state.currentRoomStatus !== ROOM_STATUS.VOTING) return;
    try {
      state.voteSnapshot = await api.votesSnapshot(state.roomCode);
    } catch (error) {
      showToast(error.message || '投票状态暂不可用', 'error');
    }
  }

  async function loadPlayerWord() {
    if (!state.roomCode || state.currentGameMode !== GAME_MODE.WORD_UNDERCOVER || state.currentRoomStatus !== ROOM_STATUS.DESCRIBING || state.wordLoading) return;
    state.wordLoading = true;
    try {
      state.currentPlayerWord = await api.playerWord(state.roomCode);
    } catch (error) {
      showToast(error.message || '关键词加载失败', 'error');
    } finally {
      state.wordLoading = false;
    }
  }

  async function loadWordDescriptions() {
    if (!state.roomCode || state.currentGameMode !== GAME_MODE.WORD_UNDERCOVER || state.currentRoomStatus !== ROOM_STATUS.DESCRIBING || state.wordDescriptionLoading) return;
    state.wordDescriptionLoading = true;
    try {
      state.wordDescriptionSnapshot = await api.wordDescriptions(state.roomCode);
    } catch (error) {
      showToast(error.message || '描述状态加载失败', 'error');
    } finally {
      state.wordDescriptionLoading = false;
    }
  }

  function enterPhase(status) {
    if (status === ROOM_STATUS.WAITING) {
      timer.clearTimer();
      setView('waiting');
      return;
    }
    if (!isGameActive(status)) {
      timer.clearTimer();
      setView('home');
      return;
    }
    setView('game');

    if (status === ROOM_STATUS.CHATTING) {
      state.voteSnapshot = null;
      state.voteCandidateIds = [];
      state.wordDescriptionSnapshot = null;
      loadChatHistory();
      timer.loadTimer(state.roomCode);
    } else if (status === ROOM_STATUS.VOTING) {
      loadVoteSnapshot();
      timer.loadTimer(state.roomCode);
    } else if (status === ROOM_STATUS.DESCRIBING) {
      timer.clearTimer();
      state.messages = [];
      loadPlayerWord();
      loadWordDescriptions();
    } else if (status === ROOM_STATUS.ENDED) {
      timer.clearTimer();
    }
  }

  function applySnapshot(snapshot) {
    if (!snapshot) return;
    state.roomCode = snapshot.roomCode || state.roomCode;
    state.currentRoomStatus = snapshot.status;
    state.currentGameMode = snapshot.gameMode || state.currentGameMode || GAME_MODE.CHAT_UNDERCOVER;
    state.currentPlayers = snapshot.players || [];
    state.currentTopic = snapshot.topic || null;
    enterPhase(snapshot.status);
  }

  async function refreshRoomState() {
    if (!state.roomCode) return;
    try {
      const snapshot = await api.snapshot(state.roomCode);
      if (snapshot.status === ROOM_STATUS.DESTROYED) {
        showToast('房间已解散');
        setView('home');
        return;
      }
      applySnapshot(snapshot);
    } catch (error) {
      if (error.code === 'ROOM_NOT_FOUND') {
        showToast('房间已解散');
        setView('home');
      }
    }
  }

  function mergeWordDescription(descriptions, nextDescription) {
    const key = wordDescriptionKey(nextDescription);
    return (descriptions || []).filter((description) => wordDescriptionKey(description) !== key).concat([nextDescription]);
  }

  function wordDescriptionKey(description) {
    return [description.playerId || '', description.createdAt || '', description.content || ''].join('|');
  }

  function handleChatMessage(payload) {
    if (!payload) return;
    state.messages.push(payload);
  }

  function handleTimerExpired(payload) {
    if (!payload) return;
    addSystemMessage(payload.phase === 'CHATTING' ? '讨论时间结束，正在进入投票阶段' : '投票时间结束，等待结算');
    timer.stopTimerInterval();
    timer.timerState.expired = true;
    window.setTimeout(refreshRoomState, payload.phase === 'CHATTING' ? 600 : 900);
  }

  function handleVotingStarted(payload) {
    if (!payload) return;
    state.currentRoomStatus = ROOM_STATUS.VOTING;
    state.voteSnapshot = null;
    state.voteCandidateIds = payload.candidatePlayerIds || [];
    addSystemMessage('进入第 ' + payload.roundNumber + ' 轮投票');
    enterPhase(ROOM_STATUS.VOTING);
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
  }

  function handleWordDescriptionSubmitted(payload) {
    if (!payload || state.currentGameMode !== GAME_MODE.WORD_UNDERCOVER) return;
    const snapshot = state.wordDescriptionSnapshot || {
      roomCode: state.roomCode,
      roundNumber: 1,
      currentPlayerId: null,
      currentNumber: null,
      roundComplete: false,
      descriptions: [],
    };
    state.wordDescriptionSnapshot = {
      ...snapshot,
      descriptions: mergeWordDescription(snapshot.descriptions, payload),
    };
    loadWordDescriptions();
  }

  function handleWordRoundStarted(payload) {
    if (!payload) return;
    state.currentGameMode = GAME_MODE.WORD_UNDERCOVER;
    state.currentRoomStatus = ROOM_STATUS.DESCRIBING;
    state.currentPlayerWord = null;
    state.wordDescriptionSnapshot = {
      roomCode: payload.roomCode || state.roomCode,
      roundNumber: payload.roundNumber,
      currentPlayerId: payload.currentPlayerId,
      currentNumber: payload.currentNumber,
      roundComplete: false,
      descriptions: [],
    };
    enterPhase(ROOM_STATUS.DESCRIBING);
  }

  function handlePlayerEliminated(payload) {
    if (!payload) return;
    const eliminated = state.currentPlayers.find((player) => player.id === payload.playerId);
    addSystemMessage('投票结算：' + playerNumberLabel(eliminated) + ' 已出局');
    state.currentPlayers = state.currentPlayers.map((player) => player.id === payload.playerId ? { ...player, alive: false } : player);
    state.currentRoomStatus = ROOM_STATUS.VOTING;
  }

  function handleRoundStarted(payload) {
    if (!payload) return;
    state.currentTopic = payload.topic || state.currentTopic;
    state.voteSnapshot = null;
    state.voteCandidateIds = [];
    state.currentRoomStatus = ROOM_STATUS.CHATTING;
    addSystemMessage('第 ' + payload.roundNumber + ' 轮讨论开始');
    refreshRoomState();
  }

  function handleGameEnded(payload) {
    if (!payload) return;
    state.currentRoomStatus = ROOM_STATUS.ENDED;
    addSystemMessage('游戏结束：' + winnerLabel(payload.winner) + '胜利');
    enterPhase(ROOM_STATUS.ENDED);
    window.setTimeout(refreshRoomState, 500);
  }

  function handleRoomEvent(event) {
    if (!event) return;
    switch (event.type) {
      case ROOM_EVENT.CHAT_MESSAGE:
        handleChatMessage(event.payload);
        break;
      case ROOM_EVENT.TIMER_EXPIRED:
        handleTimerExpired(event.payload);
        break;
      case ROOM_EVENT.VOTING_STARTED:
        handleVotingStarted(event.payload);
        break;
      case ROOM_EVENT.VOTE_UPDATED:
        handleVoteUpdated(event.payload);
        break;
      case ROOM_EVENT.PLAYER_ELIMINATED:
        handlePlayerEliminated(event.payload);
        break;
      case ROOM_EVENT.ROUND_STARTED:
        handleRoundStarted(event.payload);
        break;
      case ROOM_EVENT.WORD_DESCRIPTION_SUBMITTED:
        handleWordDescriptionSubmitted(event.payload);
        break;
      case ROOM_EVENT.WORD_ROUND_STARTED:
        handleWordRoundStarted(event.payload);
        break;
      case ROOM_EVENT.GAME_ENDED:
        handleGameEnded(event.payload);
        break;
      default:
        break;
    }
  }

  async function castVote(targetPlayerId) {
    if (!targetPlayerId || state.voteSubmitting || !isCurrentPlayerAlive()) return;
    state.voteSubmitting = true;
    try {
      const result = await api.castVote(state.roomCode, targetPlayerId);
      showToast('投票已提交');
      await loadVoteSnapshot();
      if (result?.settled) window.setTimeout(refreshRoomState, 700);
    } catch (error) {
      showToast(error.message || '投票提交失败', 'error');
      await loadVoteSnapshot();
    } finally {
      state.voteSubmitting = false;
    }
  }

  async function submitWordDescription(content) {
    const trimmed = content.trim();
    if (!trimmed || state.wordDescriptionSubmitting) return;
    state.wordDescriptionSubmitting = true;
    try {
      const result = await api.submitWordDescription(state.roomCode, trimmed);
      if (result?.snapshot) state.wordDescriptionSnapshot = result.snapshot;
      showToast('描述已提交');
      await loadWordDescriptions();
    } catch (error) {
      showToast(error.message || '描述提交失败', 'error');
    } finally {
      state.wordDescriptionSubmitting = false;
    }
  }

  return {
    applySnapshot,
    refreshRoomState,
    handleRoomEvent,
    handleChatMessage,
    handleTimerExpired,
    handleVotingStarted,
    handleVoteUpdated,
    handleWordDescriptionSubmitted,
    handleWordRoundStarted,
    handlePlayerEliminated,
    handleRoundStarted,
    handleGameEnded,
    loadChatHistory,
    loadVoteSnapshot,
    loadPlayerWord,
    loadWordDescriptions,
    castVote,
    submitWordDescription,
    addSystemMessage,
  };
}
