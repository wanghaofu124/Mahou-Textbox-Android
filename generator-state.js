(function () {
  "use strict";
  const key = "mahou.generatorState.v2";
  const legacy = { text: "mahou.lineText", character: "mahou.character", emotion: "mahou.emotion", background: "mahou.background" };
  const listeners = new Set();
  let state = { revision: -1 };
  let defaults = {};

  function normalize(value) {
    const result = { revision: Math.max(0, Number(value && value.revision) || 0) };
    for (const field of Object.keys(legacy)) {
      if (value && typeof value[field] === "string") {
        result[field] = field === "text" ? value[field].slice(0, 180) : value[field].slice(0, 160);
      }
    }
    return result;
  }

  function read() {
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.getGeneratorState === "function") {
        return normalize(JSON.parse(window.AndroidBridge.getGeneratorState() || "{}"));
      }
      return normalize(JSON.parse(localStorage.getItem(key) || "{}"));
    } catch (_) {
      return { ...defaults, revision: Math.max(0, state.revision) };
    }
  }

  function accept(value) {
    const next = { ...defaults, ...normalize(value) };
    if (next.revision < state.revision) return;
    const changed = Object.keys(legacy).filter((field) => state[field] !== next[field]);
    state = next;
    if (changed.length) for (const listener of listeners) listener({ ...state }, changed);
  }

  function update(patch) {
    const valid = normalize(patch);
    delete valid.revision;
    if (!Object.keys(valid).length) return current();
    let next;
    if (window.AndroidBridge && typeof window.AndroidBridge.updateGeneratorState === "function") {
      next = JSON.parse(window.AndroidBridge.updateGeneratorState(JSON.stringify(valid)) || "{}");
      if (next.error) throw new Error(next.error);
    } else {
      const latest = read();
      next = { ...defaults, ...latest, ...valid, revision: latest.revision + 1 };
      localStorage.setItem(key, JSON.stringify(next));
    }
    accept(next);
    return { ...state };
  }

  function initialize(initial) {
    defaults = normalize(initial);
    delete defaults.revision;
    const stored = read();
    if (stored.revision === 0) {
      const migrated = { ...defaults };
      for (const [field, oldKey] of Object.entries(legacy)) {
        const value = localStorage.getItem(oldKey);
        if (value !== null) migrated[field] = value;
      }
      return update(migrated);
    }
    accept(stored);
    return { ...state };
  }

  function current() {
    accept(read());
    return { ...state };
  }

  window.MahouState = {
    initialize,
    current,
    update,
    receive(value) {
      try { accept(typeof value === "string" ? JSON.parse(value) : value); } catch (_) {}
    },
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
  };
  window.addEventListener("storage", (event) => { if (event.key === key) accept(read()); });
})();
