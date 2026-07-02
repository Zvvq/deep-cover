import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const repoRoot = new URL('../../../', import.meta.url);
const frontendDir = new URL('src/main/frontend/', repoRoot);
const staticDir = new URL('src/main/resources/static/', repoRoot);

const packageJson = readFileSync(new URL('package.json', repoRoot), 'utf8');
const viteConfig = readFileSync(new URL('vite.config.js', repoRoot), 'utf8');
const sourceHtml = readFileSync(new URL('index.html', frontendDir), 'utf8');
const appVue = readFileSync(new URL('src/App.vue', frontendDir), 'utf8');
const sourceMain = readFileSync(new URL('src/main.js', frontendDir), 'utf8');

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

const builtHtmlPath = new URL('index.html', staticDir);
assert.ok(existsSync(builtHtmlPath), 'Vite build emits Spring static index.html');
const builtHtml = readFileSync(builtHtmlPath, 'utf8');
assert.match(builtHtml, /Deep Cover/, 'built HTML keeps product name');
assert.match(builtHtml, /type="module"/, 'built HTML uses Vite module assets');
assert.doesNotMatch(builtHtml, /app\.js\?v=/, 'built HTML no longer references the old app.js cache key');

const apiSource = readFileSync(new URL('src/composables/useRoomApi.js', frontendDir), 'utf8');
const sessionSource = readFileSync(new URL('src/composables/useRoomSession.js', frontendDir), 'utf8');
const labelSource = readFileSync(new URL('src/domain/labels.js', frontendDir), 'utf8');

for (const endpoint of ['/api/rooms', '/messages', '/timer', '/votes', '/word/me', '/word/descriptions']) {
  assert.match(apiSource, new RegExp(endpoint.replaceAll('/', '\\/')), `REST client contains ${endpoint}`);
}

for (const key of ['dc_roomCode', 'dc_playerId', 'dc_playerToken']) {
  assert.match(sessionSource, new RegExp(key), `session keeps ${key}`);
}

for (const label of ['CHAT_UNDERCOVER', 'WORD_UNDERCOVER', 'DESCRIBING', 'CHATTING', 'VOTING', 'ENDED']) {
  assert.match(labelSource, new RegExp(label), `labels include ${label}`);
}
