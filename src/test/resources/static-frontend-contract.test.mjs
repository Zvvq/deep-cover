import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const repoRoot = new URL('../../../', import.meta.url);
const frontendDir = new URL('src/main/frontend/', repoRoot);
const staticDir = new URL('src/main/resources/static/', repoRoot);

function source(relativePath) {
  return readFileSync(new URL(relativePath, frontendDir), 'utf8');
}

const packageJson = readFileSync(new URL('package.json', repoRoot), 'utf8');
const viteConfig = readFileSync(new URL('vite.config.js', repoRoot), 'utf8');
const sourceHtml = source('index.html');
const appVue = source('src/App.vue');
const sourceMain = source('src/main.js');
const apiSource = source('src/composables/useRoomApi.js');
const sessionSource = source('src/composables/useRoomSession.js');
const labelSource = source('src/domain/labels.js');
const homeView = source('src/components/HomeView.vue');
const waitingView = source('src/components/WaitingRoomView.vue');
const playerList = source('src/components/PlayerList.vue');
const gameView = source('src/components/GameView.vue');
const timerSource = source('src/composables/useGameTimer.js');
const topicPanel = source('src/components/TopicPanel.vue');
const chatPanel = source('src/components/ChatPanel.vue');
const votePanel = source('src/components/VotePanel.vue');
const wordPanel = source('src/components/WordPanel.vue');
const socketSource = source('src/composables/useRoomSocket.js');
const flowSource = source('src/composables/useGameFlow.js');

assert.match(packageJson, /"vue"\s*:/, 'package.json declares Vue');
assert.match(packageJson, /"@vitejs\/plugin-vue"\s*:/, 'package.json declares the Vite Vue plugin');
assert.match(packageJson, /"@stomp\/stompjs"\s*:/, 'package.json declares the STOMP client dependency');
assert.match(viteConfig, /src\/main\/resources\/static/, 'Vite builds into Spring static resources');
assert.match(sourceHtml, /id="app"/, 'Vue source HTML has an app mount point');
assert.match(sourceHtml, /\/api\/rooms/, 'Vue source HTML preserves the API base marker');
assert.match(sourceMain, /createApp\(App\)/, 'Vue app bootstraps with createApp');
assert.match(appVue, /HomeView/, 'App renders the home view component');
assert.match(appVue, /WaitingRoomView/, 'App renders the waiting room component');
assert.match(appVue, /GameView/, 'App renders the game view component');

for (const endpoint of ['/api/rooms', '/messages', '/timer', '/votes', '/word/me', '/word/descriptions']) {
  assert.match(apiSource, new RegExp(endpoint.replaceAll('/', '\\/')), 'REST client contains ' + endpoint);
}

for (const key of ['dc_roomCode', 'dc_playerId', 'dc_playerToken']) {
  assert.match(sessionSource, new RegExp(key), 'session keeps ' + key);
}

for (const label of ['CHAT_UNDERCOVER', 'WORD_UNDERCOVER', 'DESCRIBING', 'CHATTING', 'VOTING', 'ENDED']) {
  assert.match(labelSource, new RegExp(label), 'labels include ' + label);
}

assert.match(homeView, /CHAT_UNDERCOVER/, 'home can choose chat undercover mode');
assert.match(homeView, /WORD_UNDERCOVER/, 'home can choose word undercover mode');
assert.match(homeView, /createRoom/, 'home creates rooms through the API');
assert.match(homeView, /joinRoom/, 'home joins rooms through the API');
assert.match(waitingView, /startRoom/, 'waiting room starts the game through the API');
assert.match(waitingView, /leaveRoom/, 'waiting room can leave the room');
assert.match(waitingView, /RoomCodeBadge/, 'waiting room exposes room copy control');
assert.match(playerList, /playerNumberLabel/, 'player list renders assigned player numbers');
assert.ok(!playerList.includes('\u0041\u0049\u5367\u5e95'), 'player list does not reveal AI undercover identity');

assert.match(gameView, /TimerBadge/, 'game view renders the timer badge');
assert.match(gameView, /TopicPanel/, 'game view renders the topic panel');
assert.match(gameView, /PlayerList/, 'game view renders players');
assert.match(timerSource, /setInterval/, 'timer uses local ticking');
assert.match(timerSource, /endsAt/, 'timer uses server endsAt');
assert.match(topicPanel, /\u5f53\u524d\u8bdd\u9898/, 'topic panel labels the current topic');

assert.match(chatPanel, /chat-avatar/, 'chat messages render player avatars');
assert.match(chatPanel, /chat-msg-body/, 'chat messages keep content beside avatars');
assert.match(chatPanel, /submit/, 'chat panel submits messages');
assert.match(chatPanel, /\u53d1\u9001/, 'chat panel exposes send action');
assert.match(labelSource, /formatISOTime/, 'labels format ISO chat timestamps');

assert.match(votePanel, /castVote/, 'vote panel is wired to vote submission naming');
assert.match(votePanel, /currentPlayerVoted/, 'vote panel respects current player vote state');
assert.match(votePanel, /submittedVoteCount/, 'vote panel shows submitted vote count');
assert.match(wordPanel, /submitWordDescription/, 'word panel is wired to description submission naming');
assert.match(wordPanel, /word-description-item/, 'word panel renders submitted descriptions');
assert.match(wordPanel, /\u4f60\u7684\u5173\u952e\u8bcd/, 'word panel shows only the current player word');

assert.match(socketSource, /@stomp\/stompjs/, 'socket imports the npm STOMP client');
assert.match(socketSource, /\/ws/, 'socket connects to the Spring websocket endpoint');
assert.match(socketSource, /\/app\/rooms\/.*\/chat/, 'socket publishes chat messages');

for (const eventType of ['CHAT_MESSAGE', 'TIMER_EXPIRED', 'VOTING_STARTED', 'VOTE_UPDATED', 'PLAYER_ELIMINATED', 'ROUND_STARTED', 'WORD_DESCRIPTION_SUBMITTED', 'WORD_ROUND_STARTED', 'GAME_ENDED']) {
  assert.match(flowSource, new RegExp(eventType), 'game flow handles ' + eventType);
}

const builtHtmlPath = new URL('index.html', staticDir);
assert.ok(existsSync(builtHtmlPath), 'Vite build emits Spring static index.html');
const builtHtml = readFileSync(builtHtmlPath, 'utf8');
assert.match(builtHtml, /Deep Cover/, 'built HTML keeps product name');
assert.match(builtHtml, /type="module"/, 'built HTML uses Vite module assets');
assert.doesNotMatch(builtHtml, /app\.js\?v=/, 'built HTML no longer references the old app.js cache key');
