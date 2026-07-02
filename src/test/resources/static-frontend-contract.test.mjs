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
