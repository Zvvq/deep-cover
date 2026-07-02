import { GAME_MODE, ROOM_STATUS } from './gamePhases.js';

export function gameModeLabel(gameMode) {
  if (gameMode === GAME_MODE.WORD_UNDERCOVER) return '\u5173\u952e\u8bcd\u5367\u5e95';
  if (gameMode === GAME_MODE.CHAT_UNDERCOVER) return '\u804a\u5929\u5367\u5e95';
  return '\u672a\u77e5\u73a9\u6cd5';
}

export function roomStatusLabel(status) {
  return {
    [ROOM_STATUS.WAITING]: '\u7b49\u5f85\u4e2d',
    [ROOM_STATUS.DESCRIBING]: '\u63cf\u8ff0\u9636\u6bb5',
    [ROOM_STATUS.CHATTING]: '\u8ba8\u8bba\u9636\u6bb5',
    [ROOM_STATUS.VOTING]: '\u6295\u7968\u9636\u6bb5',
    [ROOM_STATUS.ENDED]: '\u5df2\u7ed3\u675f',
    [ROOM_STATUS.DESTROYED]: '\u5df2\u89e3\u6563',
  }[status] || '\u672a\u77e5\u72b6\u6001';
}

export function playerAvatarNumber(player) {
  if (!player || player.number == null) return '?';
  const number = Number(player.number);
  return Number.isFinite(number) ? String(number) : String(player.number);
}

export function playerNumberLabel(player) {
  if (!player || player.number == null) return '\u73a9\u5bb6';
  return `\u73a9\u5bb6${playerAvatarNumber(player)}\u53f7`;
}

export function playerTypeLabel(type) {
  if (type === 'HUMAN') return '\u771f\u4eba';
  if (type === 'AI') return 'AI';
  return '\u672a\u77e5\u8eab\u4efd';
}

export function winnerLabel(winner) {
  if (winner === 'HUMAN') return '\u771f\u4eba\u73a9\u5bb6';
  if (winner === 'AI') return 'AI \u9635\u8425';
  return winner || '\u672a\u51b3\u51fa';
}

export function formatISOTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
