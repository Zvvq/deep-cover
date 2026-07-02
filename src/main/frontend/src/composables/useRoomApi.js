export function useRoomApi(sessionState, apiBase = '/api/rooms') {
  async function request(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (sessionState.playerToken) headers['X-Player-Token'] = sessionState.playerToken;

    const options = { method, headers };
    if (body !== undefined) options.body = JSON.stringify(body);

    let response;
    try {
      response = await fetch(path, options);
    } catch {
      throw new Error('????????????');
    }

    if (!response.ok) {
      let errorBody = null;
      try {
        errorBody = await response.json();
      } catch {
        errorBody = null;
      }
      const error = new Error(errorBody?.message || `???? (${response.status})`);
      error.code = errorBody?.errorCode;
      throw error;
    }

    if (response.status === 204) return null;
    return response.json();
  }

  function roomPath(roomCode, suffix = '') {
    return `${apiBase}/${encodeURIComponent(roomCode)}${suffix}`;
  }

  return {
    request,
    createRoom(gameMode) {
      return request('POST', apiBase, { gameMode: gameMode || 'CHAT_UNDERCOVER' });
    },
    joinRoom(roomCode) {
      return request('POST', roomPath(roomCode, '/join'));
    },
    startRoom(roomCode) {
      return request('POST', roomPath(roomCode, '/start'));
    },
    leaveRoom(roomCode) {
      return request('POST', roomPath(roomCode, '/leave'));
    },
    snapshot(roomCode) {
      return request('GET', roomPath(roomCode, '/snapshot'));
    },
    messages(roomCode) {
      return request('GET', roomPath(roomCode, '/messages'));
    },
    timer(roomCode) {
      return request('GET', roomPath(roomCode, '/timer'));
    },
    votesSnapshot(roomCode) {
      return request('GET', roomPath(roomCode, '/votes'));
    },
    castVote(roomCode, targetPlayerId) {
      return request('POST', roomPath(roomCode, '/votes'), { targetPlayerId });
    },
    playerWord(roomCode) {
      return request('POST', roomPath(roomCode, '/word/me'), { playerToken: sessionState.playerToken });
    },
    wordDescriptions(roomCode) {
      return request('GET', roomPath(roomCode, '/word/descriptions'));
    },
    submitWordDescription(roomCode, content) {
      return request('POST', roomPath(roomCode, '/word/descriptions'), { content });
    },
  };
}
