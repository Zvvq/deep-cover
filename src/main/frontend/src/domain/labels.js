import { GAME_MODE, ROOM_STATUS } from './gamePhases.js';

export function gameModeLabel(gameMode) {
  if (gameMode === GAME_MODE.WORD_UNDERCOVER) return '?????';
  if (gameMode === GAME_MODE.CHAT_UNDERCOVER) return '????';
  return '????';
}

export function roomStatusLabel(status) {
  return {
    [ROOM_STATUS.WAITING]: '???',
    [ROOM_STATUS.DESCRIBING]: '????',
    [ROOM_STATUS.CHATTING]: '????',
    [ROOM_STATUS.VOTING]: '????',
    [ROOM_STATUS.ENDED]: '????',
    [ROOM_STATUS.DESTROYED]: '?????',
  }[status] || '????';
}

export function playerAvatarNumber(player) {
  if (!player || player.number == null) return '?';
  const number = Number(player.number);
  return Number.isFinite(number) ? String(number) : String(player.number);
}

export function playerNumberLabel(player) {
  if (!player || player.number == null) return '????';
  return `??${playerAvatarNumber(player)}?`;
}

export function playerTypeLabel(type) {
  if (type === 'HUMAN') return '??';
  if (type === 'AI') return 'AI';
  return '??????';
}

export function winnerLabel(winner) {
  if (winner === 'HUMAN') return '????';
  if (winner === 'AI') return 'AI ??';
  return winner || '????';
}

export function formatISOTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
