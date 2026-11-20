import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const loader = readFileSync(new URL('../assets/engine-startup.js', import.meta.url), 'utf8');
function loadWebView(supportsModules) {
    const calls = [];
    const events = new Map();
    const state = {
        window: {
            NotesApp: { startupFailed: () => calls.push('failed') },
            addEventListener: (type, handler) => events.set(type, handler),
        },
        document: {
            createElement: () => supportsModules ? { noModule: false } : {},
            head: { appendChild: script => { state.script = script; } },
        },
    };
    runInNewContext(loader, state);
    return { state, calls, events };
}
test('older WebViews receive instructions without attempting an unsupported module', () => {
    const { state, calls } = loadWebView(false);
    assert.deepEqual(calls, ['failed']);
    assert.equal(state.script, undefined);
});
test('module syntax and resource errors notify the Android app once', () => {
    const { state, events, calls } = loadWebView(true);
    assert.equal(state.script.src, '/engine.js');
    assert.equal(state.script.type, 'module');
    events.get('error')();
    state.script.onerror();
    assert.deepEqual(calls, ['failed']);
});
test('a later conversion error is handled by the running engine', () => {
    const { state, events, calls } = loadWebView(true);
    state.window.exportNote = () => {};
    events.get('error')();
    assert.deepEqual(calls, []);
});
