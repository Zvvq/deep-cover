import { computed, reactive, ref } from 'vue';

export function formatTime(seconds) {
  const safeSeconds = Math.max(0, Math.floor(Number(seconds) || 0));
  const minutes = Math.floor(safeSeconds / 60);
  const rest = safeSeconds % 60;
  return String(minutes).padStart(2, '0') + ':' + String(rest).padStart(2, '0');
}

export function useGameTimer(api) {
  const now = ref(Date.now());
  let intervalId = null;
  const timerState = reactive({
    phase: null,
    status: null,
    endsAt: null,
    serverOffsetMs: 0,
    display: '--:--',
    expired: false,
    loading: false,
  });

  const remainingSeconds = computed(() => {
    if (!timerState.endsAt) return 0;
    const adjustedNow = now.value + timerState.serverOffsetMs;
    return Math.max(0, Math.ceil((new Date(timerState.endsAt).getTime() - adjustedNow) / 1000));
  });

  function syncDisplay() {
    if (!timerState.endsAt) {
      timerState.display = '--:--';
      timerState.expired = false;
      return;
    }
    const remaining = remainingSeconds.value;
    timerState.display = formatTime(remaining);
    timerState.expired = remaining <= 0 || timerState.status === 'EXPIRED';
  }

  function startTimerInterval() {
    stopTimerInterval();
    intervalId = window.setInterval(() => {
      now.value = Date.now();
      syncDisplay();
    }, 1000);
  }

  function stopTimerInterval() {
    if (intervalId) {
      window.clearInterval(intervalId);
      intervalId = null;
    }
  }

  function clearTimer() {
    stopTimerInterval();
    timerState.phase = null;
    timerState.status = null;
    timerState.endsAt = null;
    timerState.serverOffsetMs = 0;
    timerState.display = '--:--';
    timerState.expired = false;
    timerState.loading = false;
  }

  function updateTimerDisplay(snapshot) {
    if (!snapshot) {
      clearTimer();
      return;
    }
    timerState.phase = snapshot.phase;
    timerState.status = snapshot.status;
    timerState.endsAt = snapshot.endsAt;
    timerState.serverOffsetMs = snapshot.serverNow ? new Date(snapshot.serverNow).getTime() - Date.now() : 0;
    now.value = Date.now();
    syncDisplay();
    if (snapshot.status === 'EXPIRED') stopTimerInterval();
    else startTimerInterval();
  }

  async function loadTimer(roomCode) {
    if (!roomCode) return null;
    timerState.loading = true;
    try {
      const snapshot = await api.timer(roomCode);
      updateTimerDisplay(snapshot);
      return snapshot;
    } catch {
      clearTimer();
      return null;
    } finally {
      timerState.loading = false;
    }
  }

  return { timerState, remainingSeconds, formatTime, loadTimer, updateTimerDisplay, startTimerInterval, stopTimerInterval, clearTimer };
}
