import { reactive } from 'vue';
import { GAME_MODE } from '../domain/gamePhases.js';

const STORAGE_KEYS = Object.freeze({
  roomCode: 'dc_roomCode',
  playerId: 'dc_playerId',
  playerToken: 'dc_playerToken',
});

export function createInitialSessionState() {
  return {
    roomCode: null,
    playerId: null,
    playerToken: null,
    currentRoomStatus: null,
    currentGameMode: GAME_MODE.CHAT_UNDERCOVER,
    currentPlayers: [],
    currentTopic: null,
    currentPlayerWord: null,
    wordLoading: false,
    wordDescriptionSnapshot: null,
    wordDescriptionLoading: false,
    wordDescriptionSubmitting: false,
    voteSnapshot: null,
    voteCandidateIds: [],
    voteSubmitting: false,
    messages: [],
  };
}

export function useRoomSession(storage = window.localStorage) {
  const state = reactive(createInitialSessionState());

  function persist() {
    for (const [field, key] of Object.entries(STORAGE_KEYS)) {
      if (state[field]) storage.setItem(key, state[field]);
      else storage.removeItem(key);
    }
  }

  function restore() {
    state.roomCode = storage.getItem(STORAGE_KEYS.roomCode) || null;
    state.playerId = storage.getItem(STORAGE_KEYS.playerId) || null;
    state.playerToken = storage.getItem(STORAGE_KEYS.playerToken) || null;
  }

  function setJoinedRoom(joinResult) {
    state.roomCode = joinResult.roomCode;
    state.playerId = joinResult.playerId;
    state.playerToken = joinResult.playerToken;
    if (joinResult.snapshot?.gameMode) state.currentGameMode = joinResult.snapshot.gameMode;
    persist();
  }

  function clearSession() {
    Object.assign(state, createInitialSessionState());
    persist();
  }

  return { state, persist, restore, setJoinedRoom, clearSession };
}
