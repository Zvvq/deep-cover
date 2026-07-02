export const PLAYER_COLORS = Object.freeze({
  RED: { value: '#ef4444', label: '??' },
  BLUE: { value: '#3b82f6', label: '??' },
  GREEN: { value: '#22c55e', label: '??' },
  YELLOW: { value: '#eab308', label: '??' },
  PURPLE: { value: '#a855f7', label: '??' },
  ORANGE: { value: '#f97316', label: '??' },
  CYAN: { value: '#06b6d4', label: '??' },
  PINK: { value: '#ec4899', label: '??' },
  GRAY: { value: '#94a3b8', label: '??' },
  BROWN: { value: '#92400e', label: '??' },
  LIME: { value: '#84cc16', label: '??' },
  TEAL: { value: '#14b8a6', label: '??' },
});

export function playerColorValue(color) {
  if (!color) return null;
  return PLAYER_COLORS[String(color).toUpperCase()]?.value || color;
}

export function playerColorLabel(color) {
  if (!color) return '????';
  return PLAYER_COLORS[String(color).toUpperCase()]?.label || color;
}
