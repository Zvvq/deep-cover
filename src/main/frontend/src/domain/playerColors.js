export const PLAYER_COLORS = Object.freeze({
  RED: { value: '#ef4444', label: '\u7ea2\u8272' },
  BLUE: { value: '#3b82f6', label: '\u84dd\u8272' },
  GREEN: { value: '#22c55e', label: '\u7eff\u8272' },
  YELLOW: { value: '#eab308', label: '\u9ec4\u8272' },
  PURPLE: { value: '#a855f7', label: '\u7d2b\u8272' },
  ORANGE: { value: '#f97316', label: '\u6a59\u8272' },
  CYAN: { value: '#06b6d4', label: '\u9752\u8272' },
  PINK: { value: '#ec4899', label: '\u7c89\u8272' },
  GRAY: { value: '#94a3b8', label: '\u7070\u8272' },
  BROWN: { value: '#92400e', label: '\u68d5\u8272' },
  LIME: { value: '#84cc16', label: '\u9752\u67e0' },
  TEAL: { value: '#14b8a6', label: '\u84dd\u7eff' },
});

export function playerColorValue(color) {
  if (!color) return null;
  return PLAYER_COLORS[String(color).toUpperCase()]?.value || color;
}

export function playerColorLabel(color) {
  if (!color) return '\u672a\u5206\u914d';
  return PLAYER_COLORS[String(color).toUpperCase()]?.label || color;
}
