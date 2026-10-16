const CANVAS_WIDTH = 2560;
const CANVAS_HEIGHT = 834;
const PREVIEW_MAX_WIDTH = 1280;
const SHARE_EXPORT_WIDTH = 1000;
const QQ_EXPORT_PRESETS = [
  { key: "small", label: "较小", width: 720 },
  { key: "normal", label: "标准", width: 1000 },
  { key: "large", label: "较大", width: 1280 },
  { key: "xlarge", label: "特大", width: 1600 },
];
const DEFAULT_QQ_EXPORT_KEY = "normal";
const UI_THEME_PRESETS = [
  { key: "dark", label: "深色" },
  { key: "night", label: "夜间" },
  { key: "light", label: "浅色" },
  { key: "mono", label: "黑白" },
];
const DEFAULT_UI_THEME_KEY = "dark";
const UI_THEME_META_COLORS = {
  dark: "#17181d",
  night: "#000000",
  light: "#f4f4f6",
  mono: "#0a0a0a",
};
const ROLE_TOP = 134;
const TEXT_TOP_LEFT = [728, 355];
const TEXT_BOTTOM_RIGHT = [2339, 800];

const characters = [
  { key: "ema", name: "樱羽艾玛", emotions: 8, color: [253, 145, 175], nameParts: [["樱", 759, 73, 186, [253, 145, 175]], ["羽", 949, 175, 92], ["艾", 1039, 117, 147], ["玛", 1183, 175, 92]] },
  { key: "hiro", name: "二阶堂希罗", emotions: 6, color: [239, 79, 84], nameParts: [["二", 759, 63, 196, [239, 79, 84]], ["阶堂", 955, 175, 92], ["希", 1143, 110, 147], ["罗", 1283, 175, 92]] },
  { key: "sherri", name: "橘雪莉", emotions: 7, color: [137, 177, 251], nameParts: [["橘", 759, 73, 186, [137, 177, 251]], ["雪", 943, 110, 147], ["莉", 1093, 175, 92]] },
  { key: "hanna", name: "远野汉娜", emotions: 5, color: [169, 199, 30], nameParts: [["远", 759, 73, 186, [169, 199, 30]], ["野", 945, 175, 92], ["汉", 1042, 117, 147], ["娜", 1186, 175, 92]] },
  { key: "anan", name: "夏目安安", emotions: 9, color: [159, 145, 251], nameParts: [["夏", 759, 73, 186, [159, 145, 251]], ["目", 949, 175, 92], ["安", 1039, 117, 147], ["安", 1183, 175, 92]] },
  { key: "yuki", name: "月代雪", emotions: 18, color: [195, 209, 231], nameParts: [["月", 759, 63, 196, [195, 209, 231]], ["代", 948, 175, 92], ["雪", 1053, 117, 147]] },
  { key: "meruru", name: "冰上梅露露", emotions: 6, color: [227, 185, 175], nameParts: [["冰", 759, 73, 186, [227, 185, 175]], ["上", 945, 175, 92], ["梅", 1042, 117, 147], ["露露", 1186, 175, 92]] },
  { key: "noa", name: "城崎诺亚", emotions: 6, color: [104, 223, 231], nameParts: [["城", 759, 73, 186, [104, 223, 231]], ["崎", 945, 175, 92], ["诺", 1042, 117, 147], ["亚", 1186, 175, 92]] },
  { key: "reia", name: "莲见蕾雅", emotions: 7, color: [253, 177, 88], nameParts: [["莲", 759, 73, 186, [253, 177, 88]], ["见", 945, 175, 92], ["蕾", 1042, 117, 147], ["雅", 1186, 175, 92]] },
  { key: "miria", name: "佐伯米莉亚", emotions: 4, color: [235, 207, 139], nameParts: [["佐", 759, 73, 186, [235, 207, 139]], ["伯", 945, 175, 92], ["米", 1042, 117, 147], ["莉亚", 1186, 175, 92]] },
  { key: "nanoka", name: "黑部奈叶香", emotions: 5, color: [131, 143, 147], nameParts: [["黑", 759, 63, 196, [131, 143, 147]], ["部", 955, 175, 92], ["奈", 1053, 117, 147], ["叶香", 1197, 175, 92]] },
  { key: "mago", name: "宝生玛格", emotions: 5, color: [185, 124, 235], nameParts: [["宝", 759, 73, 186, [185, 124, 235]], ["生", 945, 175, 92], ["玛", 1042, 117, 147], ["格", 1186, 175, 92]] },
  { key: "alisa", name: "紫藤亚里沙", emotions: 6, color: [235, 75, 60], nameParts: [["紫", 759, 73, 186, [235, 75, 60]], ["藤", 945, 175, 92], ["亚", 1042, 117, 147], ["里沙", 1186, 175, 92]] },
  { key: "coco", name: "泽渡可可", emotions: 5, color: [251, 114, 78], nameParts: [["泽", 759, 73, 186, [251, 114, 78]], ["渡", 945, 175, 92], ["可", 1042, 117, 147], ["可", 1186, 175, 92]] },
];

const $ = (id) => document.getElementById(id);
const canvas = $("preview");
const ctx = canvas.getContext("2d");
const characterSelect = $("characterSelect");
const emotionSelect = $("emotionSelect");
const backgroundSelect = $("backgroundSelect");
const lineText = $("lineText");
const characterPrev = $("characterPrev");
const characterNext = $("characterNext");
const emotionPrev = $("emotionPrev");
const emotionNext = $("emotionNext");
const backgroundPrev = $("backgroundPrev");
const backgroundNext = $("backgroundNext");
const characterLabel = $("characterLabel");
const emotionLabel = $("emotionLabel");
const backgroundLabel = $("backgroundLabel");
const popupButton = $("popupButton");
const randomButton = $("randomButton");
const addBackgroundButton = $("addBackgroundButton");
const assetManagerButton = $("assetManagerButton");
const qqButton = $("qqButton");
const shareButton = $("shareButton");
const aboutButton = $("aboutButton");
const qqExportSizePrev = $("qqExportSizePrev");
const qqExportSizeNext = $("qqExportSizeNext");
const qqExportSizeLabel = $("qqExportSizeLabel");
const uiThemePrev = $("uiThemePrev");
const uiThemeNext = $("uiThemeNext");
const uiThemeLabel = $("uiThemeLabel");

const imageCache = new Map();
const MAX_CACHED_IMAGES = 16;
const BUILT_IN_BACKGROUND_COUNT = 16;
const STORAGE_KEYS = {
  fallbackAssets: "mahou.fallbackAssets",
  lineText: "mahou.lineText",
  character: "mahou.character",
  emotion: "mahou.emotion",
  background: "mahou.background",
  qqExportSize: "mahou.qqExportSize",
  uiTheme: "mahou.uiTheme",
  textKeyboard: "mahou.textKeyboard",
};

let assetState = loadAssets();
let renderToken = 0;
let fontReady = null;
let pendingPick = null;
let currentAssetTab = "background";
let pendingAssetScrollId = "";
let pendingAssetHighlightId = "";
let suppressNextOverlayPop = false;
let generatorSnapshot = {};
let pendingRenderFrame = 0;
let shareBusy = false;
let previewRenderWidth = PREVIEW_MAX_WIDTH;
const overlayStack = [];
const isPopupMode = new URLSearchParams(location.search).get("popup") === "1"
  || new URLSearchParams(location.search).get("dock") === "1";

if (isPopupMode) {
  document.documentElement.classList.add("popup-mode");
}

function loadSyncedPreference(storageKey, prefsKey, normalize) {
  let key = localStorage.getItem(storageKey);
  if (window.AndroidBridge && typeof window.AndroidBridge.getPopupPreference === "function") {
    const fromBridge = window.AndroidBridge.getPopupPreference(prefsKey);
    if (fromBridge) {
      const normalizedBridge = normalize(fromBridge);
      key = normalizedBridge;
      localStorage.setItem(storageKey, normalizedBridge);
    } else if (key) {
      window.AndroidBridge.setPopupPreference(prefsKey, normalize(key));
    }
  }
  return normalize(key);
}

function saveSyncedPreference(storageKey, prefsKey, key, normalize) {
  const normalized = normalize(key);
  localStorage.setItem(storageKey, normalized);
  if (window.AndroidBridge && typeof window.AndroidBridge.setPopupPreference === "function") {
    window.AndroidBridge.setPopupPreference(prefsKey, normalized);
  }
  return normalized;
}

function normalizeUiThemeKey(key) {
  return UI_THEME_PRESETS.some((preset) => preset.key === key) ? key : DEFAULT_UI_THEME_KEY;
}

function loadUiThemeKey() {
  return loadSyncedPreference(STORAGE_KEYS.uiTheme, "uiTheme", normalizeUiThemeKey);
}

function saveUiThemeKey(key) {
  saveSyncedPreference(STORAGE_KEYS.uiTheme, "uiTheme", key, normalizeUiThemeKey);
}

function normalizeTextKeyboardMode(mode) {
  return mode === "builtin" ? "builtin" : "system";
}

function loadTextKeyboardMode() {
  return loadSyncedPreference(STORAGE_KEYS.textKeyboard, "textKeyboard", normalizeTextKeyboardMode);
}

function saveTextKeyboardMode(mode) {
  return saveSyncedPreference(STORAGE_KEYS.textKeyboard, "textKeyboard", mode, normalizeTextKeyboardMode);
}

function getUiThemePreset(key = loadUiThemeKey()) {
  const normalized = normalizeUiThemeKey(key);
  return UI_THEME_PRESETS.find((preset) => preset.key === normalized) || UI_THEME_PRESETS[0];
}

function syncNativeChromeTheme(key) {
  if (window.AndroidBridge && typeof window.AndroidBridge.applyNativeChromeTheme === "function") {
    try {
      window.AndroidBridge.applyNativeChromeTheme(key);
    } catch (error) {
      console.warn("Native theme chrome failed", error);
    }
  }
}

function applyUiTheme(key = loadUiThemeKey()) {
  const normalized = normalizeUiThemeKey(key);
  document.documentElement.dataset.theme = normalized;
  const metaTheme = document.querySelector('meta[name="theme-color"]');
  if (metaTheme) {
    metaTheme.content = UI_THEME_META_COLORS[normalized] || UI_THEME_META_COLORS.dark;
  }
  updateUiThemeLabel();
  syncNativeChromeTheme(normalized);
}

function updateUiThemeLabel() {
  if (!uiThemeLabel) return;
  uiThemeLabel.textContent = getUiThemePreset().label;
}

function stepUiTheme(delta) {
  const keys = UI_THEME_PRESETS.map((preset) => preset.key);
  const currentIndex = keys.indexOf(loadUiThemeKey());
  const nextIndex = (currentIndex + delta + keys.length) % keys.length;
  const nextKey = keys[nextIndex];
  saveUiThemeKey(nextKey);
  applyUiTheme(nextKey);
}

applyUiTheme(loadUiThemeKey());

window.__testThemeStep = function __testThemeStep(delta) {
  stepUiTheme(delta);
};

window.__testQqExportStep = function __testQqExportStep(delta) {
  stepQqExportSize(delta);
};

window.__testSetQqExport = function __testSetQqExport(key) {
  saveQqExportSizeKey(key);
  updateQqExportSizeLabel();
};

window.__testGetQqExportKey = function __testGetQqExportKey() {
  return loadQqExportSizeKey();
};

function notifyAndroid(message, visible = false) {
  if (visible && window.AndroidBridge && typeof window.AndroidBridge.showToast === "function") {
    try {
      window.AndroidBridge.showToast(message);
    } catch (error) {
      console.warn("Android toast failed", error);
    }
  }
}

function showAppNotice(message, durationMs = 1800) {
  let notice = document.querySelector(".app-notice");
  if (!notice) {
    notice = document.createElement("div");
    notice.className = "app-notice";
    notice.setAttribute("role", "status");
    notice.setAttribute("aria-live", "polite");
    document.body.appendChild(notice);
  }
  notice.textContent = message;
  notice.classList.add("open");
  clearTimeout(showAppNotice.timer);
  showAppNotice.timer = setTimeout(() => notice.classList.remove("open"), durationMs);
}

function escapeHtml(value) {
  return String(value == null ? "" : value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[character]));
}

function emptyAssets() {
  return { backgrounds: [], characters: [], emotions: [] };
}

function loadAssets() {
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.listAssets === "function") {
      const nativeAssets = JSON.parse(window.AndroidBridge.listAssets() || "{}");
      return normalizeAssets(nativeAssets);
    }
    const raw = localStorage.getItem(STORAGE_KEYS.fallbackAssets);
    return normalizeAssets(raw ? JSON.parse(raw) : emptyAssets());
  } catch (error) {
    console.warn("Failed to load assets", error);
    return emptyAssets();
  }
}

function normalizeAssets(value) {
  return {
    backgrounds: Array.isArray(value.backgrounds) ? value.backgrounds : [],
    characters: Array.isArray(value.characters) ? value.characters : [],
    emotions: Array.isArray(value.emotions) ? value.emotions : [],
  };
}

function saveFallbackAssets() {
  if (!window.AndroidBridge || typeof window.AndroidBridge.saveAsset !== "function") {
    localStorage.setItem(STORAGE_KEYS.fallbackAssets, JSON.stringify(assetState));
  }
}

function reloadAssets() {
  assetState = loadAssets();
  imageCache.clear();
}

function nativeSaveAsset(kind, metadata, dataUrl) {
  if (window.AndroidBridge && typeof window.AndroidBridge.saveAsset === "function") {
    const result = JSON.parse(window.AndroidBridge.saveAsset(kind, JSON.stringify(metadata), dataUrl || ""));
    if (result.error) throw new Error(result.error);
    reloadAssets();
    return result;
  }

  const arrayName = `${kind}s`;
  const asset = { ...metadata, kind, custom: true, src: dataUrl || metadata.src };
  assetState[arrayName] = assetState[arrayName] || [];
  const index = assetState[arrayName].findIndex((item) => item.id === asset.id);
  if (index >= 0) assetState[arrayName][index] = asset;
  else assetState[arrayName].push(asset);
  saveFallbackAssets();
  return asset;
}

function nativeSaveCharacterBundle(character, emotion, dataUrl) {
  if (window.AndroidBridge && typeof window.AndroidBridge.saveCharacterBundle === "function") {
    const result = JSON.parse(window.AndroidBridge.saveCharacterBundle(
      JSON.stringify(character),
      JSON.stringify(emotion),
      dataUrl || "",
    ));
    if (result.error) throw new Error(result.error);
    reloadAssets();
    return result;
  }
  nativeSaveAsset("character", character, dataUrl);
  try {
    nativeSaveAsset("emotion", emotion, dataUrl);
  } catch (error) {
    nativeDeleteAsset("character", character.id);
    throw error;
  }
  return { character, emotion };
}

function currentBackupSettings() {
  return {
    lineText: lineText.value,
    character: characterSelect.value,
    emotion: emotionSelect.value,
    background: backgroundSelect.value,
    qqExportSize: loadQqExportSizeKey(),
    uiTheme: loadUiThemeKey(),
    textKeyboard: loadTextKeyboardMode(),
  };
}

function applyBackupSettings(settings) {
  if (!settings || typeof settings !== "object") return;
  if (typeof settings.lineText === "string") lineText.value = settings.lineText.slice(0, 180);
  if (typeof settings.uiTheme === "string") saveUiThemeKey(settings.uiTheme);
  if (typeof settings.qqExportSize === "string") saveQqExportSizeKey(settings.qqExportSize);
  if (typeof settings.textKeyboard === "string") saveTextKeyboardMode(settings.textKeyboard);
  reloadAndRefreshSelections(settings.character, settings.background);
  if (settings.emotion && Array.from(emotionSelect.options).some((option) => option.value === settings.emotion)) {
    emotionSelect.value = settings.emotion;
  }
  updateControlLabels();
  saveGeneratorState();
  applyUiTheme(loadUiThemeKey());
  render();
}

function startAssetBackup() {
  if (!window.AndroidBridge || typeof window.AndroidBridge.startAssetBackup !== "function") {
    showAppNotice("素材备份仅在 Android App 中可用");
    return;
  }
  window.AndroidBridge.startAssetBackup(JSON.stringify(currentBackupSettings()));
}

function startAssetRestore() {
  if (!window.AndroidBridge || typeof window.AndroidBridge.startAssetRestore !== "function") {
    showAppNotice("素材恢复仅在 Android App 中可用");
    return;
  }
  window.AndroidBridge.startAssetRestore();
}

function nativeAppInfo() {
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.getAppInfo === "function") {
      return JSON.parse(window.AndroidBridge.getAppInfo() || "{}");
    }
  } catch (error) {
    console.warn("Failed to read app info", error);
  }
  return { versionName: "Web", buildType: "browser", usedBytes: 0, maxBytes: 0 };
}

function formatBytes(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function nativeUpdateAsset(id, patch) {
  if (window.AndroidBridge && typeof window.AndroidBridge.updateAsset === "function") {
    const result = JSON.parse(window.AndroidBridge.updateAsset(id, JSON.stringify(patch)));
    if (result.error) throw new Error(result.error);
    reloadAssets();
    return result;
  }

  for (const key of ["backgrounds", "characters", "emotions"]) {
    const index = assetState[key].findIndex((item) => item.id === id);
    if (index >= 0) {
      assetState[key][index] = { ...assetState[key][index], ...patch };
      saveFallbackAssets();
      return assetState[key][index];
    }
  }
  throw new Error("素材不存在");
}

function nativeDeleteAsset(kind, id) {
  if (window.AndroidBridge && typeof window.AndroidBridge.deleteAsset === "function") {
    const ok = window.AndroidBridge.deleteAsset(kind, id);
    reloadAssets();
    return ok;
  }

  const arrayName = `${kind}s`;
  assetState[arrayName] = (assetState[arrayName] || []).filter((item) => item.id !== id);
  if (kind === "character") {
    assetState.emotions = assetState.emotions.filter((item) => item.ownerId !== id);
  }
  saveFallbackAssets();
  return true;
}

function builtInBackgrounds() {
  return Array.from({ length: BUILT_IN_BACKGROUND_COUNT }, (_, index) => {
    const number = index + 1;
    return {
      key: `builtin-${number}`,
      label: `背景 ${number}`,
      src: `background/c${number}.png`,
    };
  });
}

function backgroundItems() {
  return builtInBackgrounds().concat(assetState.backgrounds.map((item, index) => ({
    key: item.id,
    id: item.id,
    custom: true,
    label: item.label || `自定义背景 ${index + 1}`,
    src: assetSource(item),
  })));
}

function assetSource(item) {
  if (item.sha256 && typeof item.src === "string" && item.src.startsWith("https://appassets.androidplatform.net/")) {
    return `${item.src}${item.src.includes("?") ? "&" : "?"}v=${encodeURIComponent(item.sha256)}`;
  }
  return item.src;
}

function customCharacters() {
  return assetState.characters.map((item, index) => {
    const emotions = assetState.emotions
      .filter((emotion) => emotion.ownerId === item.id)
      .sort((a, b) => (a.order || 0) - (b.order || 0))
      .map((emotion) => ({ ...emotion, src: assetSource(emotion) }));
    return {
      key: item.id,
      id: item.id,
      custom: true,
      name: item.label || `自定义角色 ${index + 1}`,
      emotions: Math.max(1, emotions.length),
      emotionItems: emotions,
      src: assetSource(item),
      fit: item.fit || { x: 0, y: ROLE_TOP, w: 720, h: 700 },
      nameBlocks: Array.isArray(item.nameBlocks) ? item.nameBlocks.slice(0, 32) : [{
        id: `name-${item.id}`,
        text: item.label || `角色 ${index + 1}`,
        x: 759,
        y: 73,
        size: 150,
        color: "#ffffff",
      }],
    };
  });
}

function characterItems() {
  return characters.concat(customCharacters());
}

function normalizeBackgroundKey(value) {
  if (!value) return "builtin-1";
  return /^\d+$/.test(value) ? `builtin-${value}` : value;
}

function currentBackground() {
  const key = normalizeBackgroundKey(backgroundSelect.value);
  return backgroundItems().find((item) => item.key === key) || backgroundItems()[0];
}

function refreshBackgroundOptions(preferredKey) {
  const items = backgroundItems();
  const desired = normalizeBackgroundKey(preferredKey || backgroundSelect.value);
  fillSelect(backgroundSelect, items, (item) => item.key, (item) => item.label);
  backgroundSelect.value = items.some((item) => item.key === desired) ? desired : items[0].key;
  updateControlLabels();
}

function saveGeneratorState(explicitPatch) {
  try {
    const values = { text: lineText.value, character: characterSelect.value, emotion: emotionSelect.value, background: backgroundSelect.value };
    const patch = explicitPatch || Object.fromEntries(Object.entries(values).filter(([key, value]) => generatorSnapshot[key] !== value));
    generatorSnapshot = window.MahouState.update(patch);
  } catch (error) {
    showAppNotice("未能保存当前台词，请重试");
  }
}

function applyGeneratorState(state, changedFields) {
  if (!state) return;
  let fullRefresh = !Array.isArray(changedFields);
  const changed = new Set(fullRefresh ? ["text", "character", "emotion", "background"] : changedFields);
  if (!fullRefresh && changed.size === 0) return;

  if (fullRefresh || changed.has("character") || changed.has("background")) {
    if (!characterItems().some((item) => item.key === state.character)
        || !backgroundItems().some((item) => item.key === state.background)) {
      reloadAssets();
      fullRefresh = true;
    }
  }
  generatorSnapshot = { ...state };
  if (changed.has("text") && typeof state.text === "string" && lineText.value !== state.text) lineText.value = state.text;
  if (fullRefresh || changed.has("character")) refreshCharacterOptions(state.character);
  if (fullRefresh || changed.has("background")) refreshBackgroundOptions(state.background);
  if (fullRefresh || changed.has("character") || changed.has("emotion")) updateEmotionOptions(state.emotion);
  window.dispatchEvent(new CustomEvent("generatorchange", {
    detail: { ...state, changedFields: fullRefresh ? ["text", "character", "emotion", "background"] : [...changed] },
  }));
  const textOnlyWhileEditing = changed.size === 1 && changed.has("text") && document.activeElement === lineText;
  if (!textOnlyWhileEditing) requestRender();
}

window.receiveGeneratorState = (state) => window.MahouState.receive(state);

function requestRender() {
  if (pendingRenderFrame) return;
  pendingRenderFrame = requestAnimationFrame(() => { pendingRenderFrame = 0; render(); });
}

function finishInput() {
  if (document.activeElement && typeof document.activeElement.blur === "function") document.activeElement.blur();
  document.documentElement.classList.remove("is-editing");
  if (window.AndroidBridge && typeof window.AndroidBridge.dismissKeyboard === "function") window.AndroidBridge.dismissKeyboard();
}

function rgb(values) {
  return `rgb(${values[0]}, ${values[1]}, ${values[2]})`;
}

function loadImage(src) {
  const cached = imageCache.get(src);
  if (cached) {
    imageCache.delete(src);
    imageCache.set(src, cached);
    return cached;
  }
  let pending;
  pending = new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => {
      if (imageCache.get(src) === pending) imageCache.delete(src);
      reject(new Error(`无法加载图片：${src}`));
    };
    img.src = src;
  });
  imageCache.set(src, pending);
  while (imageCache.size > MAX_CACHED_IMAGES) imageCache.delete(imageCache.keys().next().value);
  return pending;
}

function canvasToDataUrl(source, mimeType = "image/png", quality) {
  return new Promise((resolve, reject) => {
    if (typeof source.toBlob !== "function") {
      try { resolve(source.toDataURL(mimeType, quality)); }
      catch (error) { reject(error); }
      return;
    }
    source.toBlob((blob) => {
      if (!blob) { reject(new Error("无法生成图片")); return; }
      const reader = new FileReader();
      reader.onload = () => typeof reader.result === "string"
        ? resolve(reader.result) : reject(new Error("图片读取失败"));
      reader.onerror = () => reject(reader.error || new Error("图片读取失败"));
      reader.readAsDataURL(blob);
    }, mimeType, quality);
  });
}

function fillSelect(select, items, valueGetter, labelGetter) {
  select.replaceChildren(...items.map((item, index) => {
    const option = document.createElement("option");
    option.value = valueGetter(item, index);
    option.textContent = labelGetter(item, index);
    return option;
  }));
}

function currentCharacter() {
  return characterItems().find((item) => item.key === characterSelect.value) || characterItems()[2];
}

function currentEmotionItem(character) {
  if (!character.custom) return null;
  const index = Math.max(0, (Number(emotionSelect.value) || 1) - 1);
  return character.emotionItems[index] || character.emotionItems[0] || null;
}

function updateEmotionOptions(preferredValue) {
  const character = currentCharacter();
  const current = Number(preferredValue || emotionSelect.value) || 1;
  const options = character.custom
    ? (character.emotionItems.length ? character.emotionItems : [{ id: "base", label: "表情 1" }])
    : Array.from({ length: character.emotions }, (_, index) => index + 1);
  fillSelect(
    emotionSelect,
    options,
    (item, index) => character.custom ? String(index + 1) : item,
    (item, index) => character.custom ? (item.label || `表情 ${index + 1}`) : `表情 ${item}`
  );
  emotionSelect.value = String(Math.min(current, character.emotions));
  updateControlLabels();
}

function updateControlLabels() {
  const character = currentCharacter();
  const background = currentBackground();
  const emotion = currentEmotionItem(character);
  characterLabel.textContent = character.name;
  emotionLabel.textContent = emotion ? (emotion.label || `表情 ${Number(emotionSelect.value) || 1}`) : `表情 ${Number(emotionSelect.value) || 1}`;
  backgroundLabel.textContent = background.label;
}

function wrapIndex(current, length, delta) {
  return (current + delta + length) % length;
}

function stepCharacter(delta) {
  const items = characterItems();
  const current = items.findIndex((item) => item.key === characterSelect.value);
  const next = wrapIndex(current < 0 ? 0 : current, items.length, delta);
  characterSelect.value = items[next].key;
  updateEmotionOptions();
  saveGeneratorState();
  requestRender();
}

function stepNumericSelect(select, delta) {
  const options = Array.from(select.options);
  const current = Math.max(0, options.findIndex((option) => option.value === select.value));
  const next = wrapIndex(current, options.length, delta);
  select.value = options[next].value;
  updateControlLabels();
  saveGeneratorState();
  requestRender();
}

function chooseRandom() {
  const character = currentCharacter();
  const backgrounds = backgroundItems();
  emotionSelect.value = String(1 + Math.floor(Math.random() * character.emotions));
  backgroundSelect.value = backgrounds[Math.floor(Math.random() * backgrounds.length)].key;
  updateControlLabels();
  saveGeneratorState();
  requestRender();
}

function refreshCharacterOptions(preferredKey) {
  const items = characterItems();
  fillSelect(characterSelect, items, (item) => item.key, (item) => item.name);
  characterSelect.value = items.some((item) => item.key === preferredKey) ? preferredKey : (items.some((item) => item.key === characterSelect.value) ? characterSelect.value : "sherri");
}

function fitInlineComposer() {
  const stage = lineText.closest(".preview-stage");
  if (!stage) return;
  if (document.activeElement !== lineText) { stage.style.maxWidth = ""; return; }
  const composer = stage.closest(".inline-composer");
  const content = composer.parentElement;
  const styles = getComputedStyle(content);
  const siblings = [...content.children].filter((item) => item !== composer && item.getBoundingClientRect().height > 0);
  const otherHeight = siblings.reduce((sum, item) => sum + item.getBoundingClientRect().height, 0);
  const toolsHeight = composer.querySelector(".composer-tools").getBoundingClientRect().height;
  const room = content.clientHeight - otherHeight - toolsHeight - 2
    - (parseFloat(styles.rowGap) || 0) * siblings.length
    - (parseFloat(styles.paddingTop) || 0) - (parseFloat(styles.paddingBottom) || 0);
  // Keep the image and controls inside the keyboard-reduced viewport without stretching it.
  stage.style.maxWidth = `${Math.max(1, room) * CANVAS_WIDTH / CANVAS_HEIGHT}px`;
  content.scrollTop = 0;
}

window.setupImageDoubleTap = function setupImageDoubleTap(surface, action) {
  let down = null, previous = null, pointerActivatedAt = -1000;
  function activate() {
    action();
  }
  surface.setAttribute("role", "button");
  surface.setAttribute("aria-label", "编辑图片内台词");
  surface.tabIndex = 0;
  surface.addEventListener("pointerdown", (event) => {
    if (event.isPrimary && event.button === 0) down = { x: event.clientX, y: event.clientY, time: event.timeStamp, id: event.pointerId };
  });
  surface.addEventListener("pointercancel", () => { down = previous = null; });
  surface.addEventListener("pointerup", (event) => {
    if (!down || down.id !== event.pointerId) return;
    const moved = Math.hypot(event.clientX - down.x, event.clientY - down.y);
    const tap = moved < 12 && event.timeStamp - down.time < 350;
    down = null;
    if (!tap) { previous = null; return; }
    if (previous && event.timeStamp - previous.time < 360 && Math.hypot(event.clientX - previous.x, event.clientY - previous.y) < 24) {
      previous = null;
      pointerActivatedAt = event.timeStamp;
      activate();
    } else previous = { x: event.clientX, y: event.clientY, time: event.timeStamp };
  });
  surface.addEventListener("dblclick", (event) => {
    event.preventDefault();
    if (event.timeStamp - pointerActivatedAt > 500) activate();
  });
  surface.addEventListener("click", (event) => { if (event.detail === 0) activate(); });
  surface.addEventListener("keydown", (event) => {
    if (event.key === "Enter" || event.key === " ") { event.preventDefault(); activate(); }
  });
};

window.beginInlineTextEditing = function beginInlineTextEditing() {
  if (!lineText.classList.contains("inline-line-text")) return;
  const alreadyEditing = document.activeElement === lineText;
  lineText.readOnly = false;
  lineText.tabIndex = 0;
  lineText.focus();
  if (!alreadyEditing) lineText.setSelectionRange(lineText.value.length, lineText.value.length);
};

function setupControls() {
  generatorSnapshot = window.MahouState.initialize({ text: lineText.value, character: "ema", emotion: "1", background: "builtin-1" });
  applyGeneratorState(generatorSnapshot);
  window.MahouState.subscribe(applyGeneratorState);

  characterSelect.addEventListener("change", () => {
    updateEmotionOptions();
    saveGeneratorState();
    requestRender();
  });
  emotionSelect.addEventListener("change", () => {
    saveGeneratorState();
    requestRender();
  });
  backgroundSelect.addEventListener("change", () => {
    updateControlLabels();
    saveGeneratorState();
    requestRender();
  });
  characterPrev.addEventListener("click", () => stepCharacter(-1));
  characterNext.addEventListener("click", () => stepCharacter(1));
  emotionPrev.addEventListener("click", () => stepNumericSelect(emotionSelect, -1));
  emotionNext.addEventListener("click", () => stepNumericSelect(emotionSelect, 1));
  backgroundPrev.addEventListener("click", () => stepNumericSelect(backgroundSelect, -1));
  backgroundNext.addEventListener("click", () => stepNumericSelect(backgroundSelect, 1));
  lineText.addEventListener("input", () => {
    saveGeneratorState({ text: lineText.value });
    if (document.activeElement !== lineText) requestRender();
  });
  if (lineText.classList.contains("inline-line-text")) {
    const refreshEditor = () => { requestAnimationFrame(fitInlineComposer); requestRender(); };
    lineText.addEventListener("focus", refreshEditor);
    lineText.addEventListener("blur", () => { lineText.readOnly = true; lineText.tabIndex = -1; refreshEditor(); });
    if (window.ResizeObserver) new ResizeObserver(fitInlineComposer).observe(lineText.closest(".workspace-content"));
    else window.addEventListener("resize", fitInlineComposer);
    window.setupImageDoubleTap(canvas, window.beginInlineTextEditing);
    $("editTextButton")?.addEventListener("click", window.beginInlineTextEditing);
    for (const id of ["editTextButton", "randomButton", "clearTextButton"]) {
      $(id)?.addEventListener("pointerdown", (event) => {
        if (document.activeElement === lineText) event.preventDefault();
      });
    }
  }
  setupKeyboardDoneButton();
  popupButton?.addEventListener("click", startPopupMode);
  randomButton?.addEventListener("click", chooseRandom);
  addBackgroundButton?.addEventListener("click", addBackground);
  assetManagerButton?.addEventListener("click", openAssetManager);
  aboutButton?.addEventListener("click", openAboutModal);
  qqButton?.addEventListener("click", shareToQQ);
  shareButton?.addEventListener("click", shareImage);
  if (qqExportSizePrev && qqExportSizeNext) {
    updateQqExportSizeLabel();
    qqExportSizePrev.addEventListener("click", () => stepQqExportSize(-1));
    qqExportSizeNext.addEventListener("click", () => stepQqExportSize(1));
  }
  if (uiThemePrev && uiThemeNext) {
    updateUiThemeLabel();
    uiThemePrev.addEventListener("click", () => stepUiTheme(-1));
    uiThemeNext.addEventListener("click", () => stepUiTheme(1));
  }
  if (isPopupMode && popupButton) {
    popupButton.setAttribute("hidden", "hidden");
  }
  updateControlLabels();
}

function setupKeyboardDoneButton() {
  const existing = document.querySelector("[data-keyboard-done]");
  if (existing) { existing.addEventListener("click", finishInput); return; }
  const button = document.createElement("button");
  button.type = "button";
  button.className = "keyboard-done";
  button.dataset.keyboardDone = "true";
  button.textContent = "完成输入";
  button.setAttribute("aria-label", "收起键盘并完成输入");
  button.addEventListener("click", () => {
    finishInput();
    requestRender();
  });
  lineText.insertAdjacentElement("afterend", button);
}

function setTextFont(size, context = ctx) {
  context.font = `${size}px MahouFont, "PingFang SC", "Microsoft YaHei", sans-serif`;
  context.textBaseline = "top";
}

function wrapParagraph(paragraph, maxWidth, context = ctx) {
  const units = paragraph.includes(" ") ? paragraph.split(/(\s+)/) : Array.from(paragraph);
  const lines = [];
  let buffer = "";

  for (const unit of units) {
    const trial = buffer + unit;
    if (context.measureText(trial).width <= maxWidth) {
      buffer = trial;
      continue;
    }

    if (buffer.trim().length > 0 || !paragraph.includes(" ")) {
      lines.push(buffer.trimEnd());
    }
    buffer = unit.trimStart();

    while (context.measureText(buffer).width > maxWidth && buffer.length > 1) {
      let slice = "";
      for (const char of Array.from(buffer)) {
        if (context.measureText(slice + char).width > maxWidth) break;
        slice += char;
      }
      lines.push(slice);
      buffer = buffer.slice(slice.length);
    }
  }

  if (buffer.length > 0) {
    lines.push(buffer.trimEnd());
  }
  return lines.length ? lines : [""];
}

function wrapText(text, fontSize, maxWidth, context = ctx) {
  setTextFont(fontSize, context);
  return text.split(/\r?\n/).flatMap((paragraph) => wrapParagraph(paragraph, maxWidth, context));
}

function measureBlock(lines, fontSize, context = ctx) {
  setTextFont(fontSize, context);
  const lineHeight = Math.round(fontSize * 1.22);
  const width = Math.max(0, ...lines.map((line) => context.measureText(line).width));
  return { width, height: Math.max(lineHeight, lines.length * lineHeight), lineHeight };
}

function fitText(text, maxWidth, maxHeight, context = ctx) {
  let lo = 12;
  let hi = 145;
  let best = { size: 12, lines: wrapText(text, 12, maxWidth, context), lineHeight: 15, height: 15 };

  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    const lines = wrapText(text, mid, maxWidth, context);
    const block = measureBlock(lines, mid, context);
    if (block.width <= maxWidth && block.height <= maxHeight) {
      best = { size: mid, lines, lineHeight: block.lineHeight, height: block.height };
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return best;
}

function drawSegmentedText(text, x, y, baseColor, bracketColor, context = ctx) {
  let activeBracket = false;
  let cursor = x;

  for (const char of Array.from(text)) {
    if (char === "[" || char === "【") {
      activeBracket = true;
    }

    const color = activeBracket ? bracketColor : baseColor;
    context.fillStyle = "rgba(0, 0, 0, 0.82)";
    context.fillText(char, cursor + 4, y + 4);
    context.fillStyle = color;
    context.fillText(char, cursor, y);
    cursor += context.measureText(char).width;

    if (char === "]" || char === "】") {
      activeBracket = false;
    }
  }
}

function drawDialogText(character, context = ctx) {
  const [x1, y1] = TEXT_TOP_LEFT;
  const [x2, y2] = TEXT_BOTTOM_RIGHT;
  const text = lineText.value.trim() || " ";
  const fitted = fitText(text, x2 - x1, y2 - y1, context);
  const bracketColor = character.key === "anan" ? rgb(character.color) : "rgb(137, 177, 251)";
  setTextFont(fitted.size, context);

  let y = y1;
  for (const line of fitted.lines) {
    drawSegmentedText(line, x1, y, "rgb(255, 255, 255)", bracketColor, context);
    y += fitted.lineHeight;
    if (y > y2) break;
  }
}

function drawCharacterName(character, context = ctx) {
  if (character.custom) {
    for (const block of character.nameBlocks || []) {
      const text = block.text || "";
      if (!text) continue;
      const size = Number(block.size) || 120;
      context.font = `${size}px MahouFont, "PingFang SC", "Microsoft YaHei", sans-serif`;
      context.textBaseline = "top";
      context.fillStyle = "rgb(0, 0, 0)";
      context.fillText(text, Number(block.x) + 3, Number(block.y) + 3);
      context.fillStyle = block.color || "#ffffff";
      context.fillText(text, Number(block.x), Number(block.y));
    }
    return;
  }

  for (const [text, x, y, size, color] of character.nameParts) {
    if (!text) continue;
    context.font = `${size}px MahouFont, "PingFang SC", "Microsoft YaHei", sans-serif`;
    context.textBaseline = "top";
    context.fillStyle = "rgb(0, 0, 0)";
    context.fillText(text, x + 2, y + 2);
    context.fillStyle = color ? rgb(color) : "rgb(255, 255, 255)";
    context.fillText(text, x, y);
  }
}

function roleSource(character, emotionIndex) {
  if (!character.custom) {
    return `${character.key}/${character.key} (${emotionIndex}).png`;
  }
  const emotion = currentEmotionItem(character);
  return (emotion && emotion.src) || character.src;
}

function drawRoleImage(role, character, context = ctx) {
  if (!character.custom) {
    context.drawImage(role, 0, ROLE_TOP);
    return;
  }
  const fit = character.fit || { x: 0, y: ROLE_TOP, w: 720, h: 700 };
  context.drawImage(role, Number.isFinite(Number(fit.x)) ? Number(fit.x) : 0, Number.isFinite(Number(fit.y)) ? Number(fit.y) : ROLE_TOP, Number(fit.w) || 720, Number(fit.h) || 700);
}

function ensureFont() {
  if (!fontReady) fontReady = document.fonts.load("145px MahouFont");
  return fontReady;
}

async function render(options = {}) {
  const token = ++renderToken;
  const targetCanvas = options.targetCanvas || canvas;
  const requestedWidth = options.targetWidth || (targetCanvas === canvas ? getPreviewRenderWidth() : CANVAS_WIDTH);
  const targetWidth = Math.max(1, Math.min(CANVAS_WIDTH, Math.round(requestedWidth)));
  const targetHeight = Math.round(CANVAS_HEIGHT * targetWidth / CANVAS_WIDTH);
  const scale = targetWidth / CANVAS_WIDTH;
  const character = currentCharacter();
  const backgroundItem = currentBackground();
  const emotionIndex = Number(emotionSelect.value) || 1;

  try {
    const [backgroundImage, role] = await Promise.all([
      loadImage(backgroundItem.src),
      loadImage(roleSource(character, emotionIndex)),
      ensureFont(),
    ]);
    if (token !== renderToken) return false;
    if (targetCanvas.width !== targetWidth || targetCanvas.height !== targetHeight) {
      targetCanvas.width = targetWidth;
      targetCanvas.height = targetHeight;
    }
    const context = targetCanvas.getContext("2d");
    if (!context) return false;
    context.setTransform(scale, 0, 0, scale, 0, 0);
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.clearRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    context.fillStyle = "#111218";
    context.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    context.drawImage(backgroundImage, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    drawRoleImage(role, character, context);
    const editingInline = (lineText.classList.contains("inline-line-text") && document.activeElement === lineText)
      || Boolean(window.isImeDraftEditing?.());
    if (options.forceDialogText || !editingInline) drawDialogText(character, context);
    drawCharacterName(character, context);
    return true;
  } catch (error) {
    if (token !== renderToken) return false;
    if (targetCanvas.width !== targetWidth || targetCanvas.height !== targetHeight) {
      targetCanvas.width = targetWidth;
      targetCanvas.height = targetHeight;
    }
    const context = targetCanvas.getContext("2d");
    if (context) {
      context.setTransform(scale, 0, 0, scale, 0, 0);
      context.fillStyle = "#111218";
      context.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
      context.fillStyle = "#fff";
      context.font = "48px sans-serif";
      context.fillText("素材无法加载，请重新选择", 60, 80);
    }
    return false;
  }
}

function getPreviewRenderWidth() {
  const cssWidth = canvas.clientWidth;
  if (!cssWidth) return previewRenderWidth;
  const pixelRatio = Math.min(2, Math.max(1, Number(window.devicePixelRatio) || 1));
  previewRenderWidth = Math.max(360, Math.min(CANVAS_WIDTH, PREVIEW_MAX_WIDTH, Math.round(cssWidth * pixelRatio)));
  return previewRenderWidth;
}

function dataUrlToBlob(dataUrl) {
  const separator = dataUrl.indexOf(",");
  if (separator < 0) throw new Error("图片数据无效");
  const header = dataUrl.slice(0, separator);
  const mime = (header.match(/^data:([^;]+)/) || [])[1] || "image/png";
  const binary = atob(dataUrl.slice(separator + 1));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return new Blob([bytes], { type: mime });
}

function normalizeQqExportSizeKey(key) {
  return QQ_EXPORT_PRESETS.some((preset) => preset.key === key) ? key : DEFAULT_QQ_EXPORT_KEY;
}

function getQqExportPreset(key = loadQqExportSizeKey()) {
  const normalized = normalizeQqExportSizeKey(key);
  return QQ_EXPORT_PRESETS.find((preset) => preset.key === normalized) || QQ_EXPORT_PRESETS[1];
}

function loadQqExportSizeKey() {
  return loadSyncedPreference(STORAGE_KEYS.qqExportSize, "qqExportSize", normalizeQqExportSizeKey);
}

function saveQqExportSizeKey(key) {
  saveSyncedPreference(STORAGE_KEYS.qqExportSize, "qqExportSize", key, normalizeQqExportSizeKey);
}

function getQqExportMaxWidth() {
  return getQqExportPreset().width;
}

function updateQqExportSizeLabel() {
  if (!qqExportSizeLabel) return;
  qqExportSizeLabel.textContent = getQqExportPreset().label;
}

function stepQqExportSize(delta) {
  const keys = QQ_EXPORT_PRESETS.map((preset) => preset.key);
  const currentIndex = keys.indexOf(loadQqExportSizeKey());
  const nextIndex = (currentIndex + delta + keys.length) % keys.length;
  saveQqExportSizeKey(keys[nextIndex]);
  updateQqExportSizeLabel();
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error || new Error("读取图片失败"));
    reader.readAsDataURL(file);
  });
}

function loadStandaloneImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("图片加载失败"));
    img.src = src;
  });
}

async function normalizeCustomImage(dataUrl, maxEdge = 1280) {
  const img = await loadStandaloneImage(dataUrl);
  const longest = Math.max(img.naturalWidth, img.naturalHeight);
  const scale = Math.min(1, maxEdge / longest);
  const exportCanvas = document.createElement("canvas");
  exportCanvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
  exportCanvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
  exportCanvas.getContext("2d").drawImage(img, 0, 0, exportCanvas.width, exportCanvas.height);
  return canvasToDataUrl(exportCanvas, "image/jpeg", 0.88);
}

async function addBackgroundFromBrowserFile(file) {
  const rawDataUrl = await readFileAsDataUrl(file);
  openImageEditor("background", "", rawDataUrl);
}

function pickAssetImage(kind, ownerId = "") {
  pendingPick = { kind, ownerId };
  if (window.AndroidBridge && typeof window.AndroidBridge.pickAssetImage === "function") {
    window.AndroidBridge.pickAssetImage(kind, ownerId);
    return;
  }
  if (kind === "background" && window.AndroidBridge && typeof window.AndroidBridge.pickBackgroundImage === "function") {
    window.AndroidBridge.pickBackgroundImage();
    return;
  }

  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/*";
  input.addEventListener("change", async () => {
    const file = input.files && input.files[0];
    if (!file) return;
    try {
      const dataUrl = await readFileAsDataUrl(file);
      window.receivePickedAsset(kind, ownerId, dataUrl);
    } catch (error) {
      alert(`选择图片失败：${error.message}`);
    }
  }, { once: true });
  input.click();
}

function addBackground() {
  pickAssetImage("background", "");
}

async function requestAssetImage(kind, ownerId = "") {
  if (kind === "character" || kind === "emotion") {
    const ok = await showConfirmDialog({
      title: "导入素材提示",
      message: kind === "character"
        ? "角色图片推荐使用透明 PNG 或干净立绘。截图、不透明背景图也可以导入，但生成效果可能不适合作为角色。"
        : "表情图片推荐使用同一角色的透明 PNG 或干净头像。截图、不透明背景图也可以导入，但生成效果可能不适合作为表情。",
      confirmText: "继续选择",
      cancelText: "取消",
      danger: false,
    });
    if (!ok) return;
  }
  pickAssetImage(kind, ownerId);
}

function uid(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

function openOverlay(element, historyName) {
  if (element.classList.contains("open")) return;
  const returnFocus = document.activeElement;
  finishInput();
  overlayStack.push({ element, returnFocus });
  element.setAttribute("role", "dialog");
  element.setAttribute("aria-modal", "true");
  element.tabIndex = -1;
  element.classList.add("open");
  if (!element.dataset.historyOpen && !document.documentElement.classList.contains("ime-mode")) {
    element.dataset.historyOpen = historyName;
    history.pushState({ mahouOverlay: historyName }, "", location.href);
  }
  syncOverlayFocus();
  const first = element.querySelector("[autofocus], button:not([disabled]), input, textarea");
  requestAnimationFrame(() => (first || element).focus({ preventScroll: true }));
}

function closeOverlay(element, syncHistory = true) {
  if (!element || !element.classList.contains("open")) return;
  const entry = overlayStack.find((item) => item.element === element);
  element.classList.remove("open");
  const index = overlayStack.findIndex((item) => item.element === element);
  if (index >= 0) overlayStack.splice(index, 1);
  delete element.dataset.historyOpen;
  if (syncHistory && history.state && history.state.mahouOverlay) {
    suppressNextOverlayPop = true;
    history.back();
  }
  syncOverlayFocus();
  if (entry && entry.returnFocus && entry.returnFocus.isConnected && !entry.returnFocus.closest("[inert]")) {
    entry.returnFocus.focus({ preventScroll: true });
  }
}

function activeOverlay() {
  return overlayStack.length ? overlayStack[overlayStack.length - 1].element : null;
}

function syncOverlayFocus() {
  const active = activeOverlay();
  for (const element of document.body.children) {
    if (element.tagName === "SCRIPT" || element.classList.contains("app-notice")) continue;
    element.inert = Boolean(active && element !== active);
    if (element.inert) element.setAttribute("aria-hidden", "true");
    else element.removeAttribute("aria-hidden");
  }
  if (window.AndroidBridge && typeof window.AndroidBridge.setEditorExpanded === "function") {
    window.AndroidBridge.setEditorExpanded(Boolean(active));
  }
  scheduleAccessibilityReport();
}

function closeTopOverlayFromHistory() {
  const active = activeOverlay();
  if (!active) return false;
  if (active.finish) active.finish(false, false);
  else closeOverlay(active, false);
  return true;
}

window.handleAndroidBack = function handleAndroidBack() {
  if (document.documentElement.classList.contains("is-editing")) { finishInput(); return true; }
  const active = activeOverlay();
  if (active) {
    if (active.finish) active.finish(false);
    else closeOverlay(active);
    return true;
  }
  return false;
};

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && window.handleAndroidBack()) { event.preventDefault(); return; }
  const active = activeOverlay();
  if (event.key !== "Tab" || !active) return;
  const items = Array.from(active.querySelectorAll('button:not([disabled]),input:not([disabled]),textarea,[tabindex="0"]'))
    .filter((item) => !item.hidden && item.getBoundingClientRect().width > 0);
  if (!items.length) { event.preventDefault(); active.focus(); return; }
  if (event.shiftKey && (document.activeElement === items[0] || !active.contains(document.activeElement))) {
    event.preventDefault(); items[items.length - 1].focus();
  } else if (!event.shiftKey && (document.activeElement === items[items.length - 1] || !active.contains(document.activeElement))) {
    event.preventDefault(); items[0].focus();
  }
});

document.addEventListener("focusin", (event) => {
  const editable = event.target.matches("textarea,input:not([type=range]):not([type=color])");
  document.documentElement.classList.toggle("is-editing", editable);
  if (window.AndroidBridge && typeof window.AndroidBridge.setEditing === "function") window.AndroidBridge.setEditing(editable);
});
document.addEventListener("focusout", () => setTimeout(() => {
  if (!document.activeElement.matches("textarea,input:not([type=range]):not([type=color])")) {
    document.documentElement.classList.remove("is-editing");
    if (window.AndroidBridge && typeof window.AndroidBridge.setEditing === "function") window.AndroidBridge.setEditing(false);
  }
}, 0));

window.addEventListener("popstate", () => {
  if (suppressNextOverlayPop) {
    suppressNextOverlayPop = false;
    return;
  }
  closeTopOverlayFromHistory();
});

function scrollAssetIntoView(id) {
  if (!id) return;
  const doScroll = () => {
    const card = Array.from(document.querySelectorAll("[data-asset-id]"))
      .find((item) => item.dataset.assetId === id);
    if (card) card.scrollIntoView({ block: "center", inline: "nearest", behavior: "smooth" });
  };
  requestAnimationFrame(doScroll);
  setTimeout(doScroll, 250);
  setTimeout(doScroll, 800);
}

function showConfirmDialog({ title, message, confirmText = "删除", cancelText = "取消", danger = true, inputValue = null }) {
  return new Promise((resolve) => {
    let dialog = document.querySelector(".confirm-dialog");
    if (!dialog) {
      dialog = document.createElement("section");
      dialog.className = "confirm-dialog";
      dialog.innerHTML = `
        <div class="confirm-card" role="dialog" aria-modal="true">
          <strong data-confirm-title></strong>
          <p data-confirm-message></p>
          <input data-confirm-input aria-label="素材名称" maxlength="80" hidden />
          <div class="confirm-actions">
            <button type="button" data-confirm-cancel></button>
            <button type="button" data-confirm-ok></button>
          </div>
        </div>
      `;
      document.body.appendChild(dialog);
    }
    dialog.querySelector("[data-confirm-title]").textContent = title;
    dialog.querySelector("[data-confirm-message]").textContent = message;
    const input = dialog.querySelector("[data-confirm-input]");
    input.hidden = inputValue === null;
    input.value = inputValue || "";
    dialog.querySelector("[data-confirm-cancel]").textContent = cancelText;
    const ok = dialog.querySelector("[data-confirm-ok]");
    ok.textContent = confirmText;
    ok.classList.toggle("danger", danger);
    openOverlay(dialog, "confirm");
    const finish = (value, syncHistory = true) => {
      closeOverlay(dialog, syncHistory);
      dialog.querySelector("[data-confirm-cancel]").onclick = null;
      ok.onclick = null;
      dialog.finish = null;
      resolve(inputValue === null ? value : (value ? input.value.trim() : null));
    };
    dialog.finish = finish;
    dialog.querySelector("[data-confirm-cancel]").onclick = () => finish(false);
    ok.onclick = () => finish(true);
    if (inputValue !== null) requestAnimationFrame(() => { input.focus(); input.select(); });
  });
}

function ensureAssetModal() {
  let modal = document.querySelector(".asset-modal");
  if (modal) return modal;
  modal = document.createElement("section");
  modal.className = "asset-modal";
  modal.innerHTML = `
    <div class="asset-header">
      <strong>素材管理</strong>
      <button type="button" data-close-assets aria-label="关闭素材管理">关闭</button>
    </div>
    <div class="asset-transfer-bar">
      <output data-storage-usage aria-label="自定义素材已用空间">正在读取空间…</output>
      <button type="button" data-backup-assets>备份</button>
      <button type="button" data-restore-assets>恢复</button>
    </div>
    <div class="asset-tabs" role="tablist">
      <button type="button" data-tab="background" class="active">背景</button>
      <button type="button" data-tab="character">角色</button>
      <button type="button" data-tab="emotion">表情</button>
    </div>
    <div class="asset-content"></div>
  `;
  document.body.appendChild(modal);
  modal.querySelector("[data-close-assets]").addEventListener("click", () => closeOverlay(modal));
  modal.querySelector("[data-backup-assets]").addEventListener("click", startAssetBackup);
  modal.querySelector("[data-restore-assets]").addEventListener("click", startAssetRestore);
  modal.querySelectorAll("[data-tab]").forEach((button) => {
    button.addEventListener("click", () => {
      currentAssetTab = button.dataset.tab;
      renderAssetManager({ resetScroll: true });
    });
  });
  return modal;
}

function openAssetManager() {
  reloadAssets();
  const modal = ensureAssetModal();
  openOverlay(modal, "assets");
  renderAssetManager();
  updateStorageUsage();
}

function ensureAboutModal() {
  let modal = document.querySelector(".about-modal");
  if (modal) return modal;
  modal = document.createElement("section");
  modal.className = "about-modal";
  modal.innerHTML = `
    <div class="asset-header">
      <strong>关于与隐私</strong>
      <button type="button" data-close-about aria-label="关闭关于与隐私">关闭</button>
    </div>
    <div class="about-content">
      <dl class="about-facts">
        <div><dt>版本</dt><dd data-about-version>正在读取…</dd></div>
        <div><dt>更新</dt><dd>请从原下载渠道手动获取新版</dd></div>
      </dl>
      <h2>本地与隐私</h2>
      <p>台词和素材保存在本机，离线拼音也不联网。读取草稿仅在你点击后执行，密码不会读取或保存。发送按钮会尝试把图片放进 QQ 输入框；若 QQ 不接收，图片会保存到相册，请点输入框右侧“+”→“相册”选择最新的魔裁图片发送。应用不会自动替你发出消息或选择联系人，也不需要 Root。</p>
      <p>离线拼音使用 simple-ime（MIT）、dawg-lookup（MIT）和 lru-cache（ISC），许可证随应用附带。</p>
      <h2>权限用途</h2>
      <p>悬浮窗权限用于在 QQ 等应用上方显示生成器；通知权限用于提示悬浮窗正在运行，并提供恢复和关闭入口；文件选择器仅在你导入、备份或恢复素材时打开。麦克风权限仅在你点内置键盘的语音按钮时请求，手机的语音识别服务可能联网处理语音。</p>
      <h2>项目与许可</h2>
      <p>本项目基于原魔法少女文本框工具继续开发，程序代码依照 MIT License 提供。内置角色与背景由发布者确认拥有公开分发权。</p>
      <p class="about-limit">应用不能自动选择 QQ 群或代替用户发送消息。</p>
    </div>
  `;
  document.body.appendChild(modal);
  modal.querySelector("[data-close-about]").addEventListener("click", () => closeOverlay(modal));
  return modal;
}

function openAboutModal() {
  const modal = ensureAboutModal();
  const info = nativeAppInfo();
  modal.querySelector("[data-about-version]").textContent = `${info.versionName || "未知"} (${info.buildType || ""})`;
  openOverlay(modal, "about");
}

function updateStorageUsage() {
  const output = ensureAssetModal().querySelector("[data-storage-usage]");
  const info = nativeAppInfo();
  output.textContent = info.maxBytes
    ? `已用 ${formatBytes(info.usedBytes)} / ${formatBytes(info.maxBytes)}`
    : "浏览器本地素材";
}

function renderAssetManager(options = {}) {
  const modal = ensureAssetModal();
  modal.querySelectorAll("[data-tab]").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === currentAssetTab);
  });
  const content = modal.querySelector(".asset-content");
  if (currentAssetTab === "background") renderBackgroundManager(content);
  if (currentAssetTab === "character") renderCharacterManager(content);
  if (currentAssetTab === "emotion") renderEmotionManager(content);
  if (options.resetScroll) content.scrollTop = 0;
  markAssetSelection();
  if (pendingAssetScrollId) {
    scrollAssetIntoView(pendingAssetScrollId);
    pendingAssetScrollId = "";
  }
  updateStorageUsage();
}

function markAssetSelection(event) {
  const changedFields = event && event.detail && event.detail.changedFields;
  if (Array.isArray(changedFields) && changedFields.length === 1 && changedFields[0] === "text") return;
  const modal = document.querySelector(".asset-modal.open");
  if (!modal) return;
  const character = currentCharacter();
  const selected = currentAssetTab === "background" ? backgroundSelect.value
    : currentAssetTab === "character" ? characterSelect.value
      : character.custom ? currentEmotionItem(character)?.id : `${character.key}-${emotionSelect.value}`;
  modal.querySelectorAll(".asset-card").forEach((card) => {
    const active = card.dataset.assetId === selected;
    card.classList.toggle("is-selected", active);
    const use = card.querySelector("[data-use]");
    if (use) { use.textContent = active ? "当前" : "使用"; use.setAttribute("aria-pressed", String(active)); }
  });
}
window.addEventListener("generatorchange", markAssetSelection);

function renderBackgroundManager(content) {
  const items = backgroundItems();
  content.innerHTML = `
    <div class="asset-actions">
      <button type="button" data-add-background>添加背景</button>
    </div>
    <div class="asset-grid"></div>
    <div class="asset-footer-actions">
      <button type="button" data-add-background>添加背景</button>
      <button type="button" data-scroll-top>回到顶部</button>
    </div>
  `;
  content.querySelectorAll("[data-add-background]").forEach((button) => {
    button.addEventListener("click", addBackground);
  });
  content.querySelector("[data-scroll-top]").addEventListener("click", () => content.scrollTo({ top: 0, behavior: "smooth" }));
  const grid = content.querySelector(".asset-grid");
  for (const item of items) {
    const card = document.createElement("article");
    card.className = "asset-card";
    if ((item.id || item.key) === pendingAssetHighlightId) card.classList.add("is-highlighted");
    card.dataset.assetId = item.id || item.key;
    card.innerHTML = `
      <img src="${escapeHtml(item.src)}" alt="${escapeHtml(item.label)}" loading="lazy">
      <strong>${escapeHtml(item.label)}</strong>
      <div class="card-actions">
        <button type="button" data-use>使用</button>
        ${item.custom ? '<button type="button" data-rename>改名</button><button type="button" data-delete>删除</button>' : '<button type="button" disabled>内置</button>'}
      </div>
    `;
    card.querySelector("[data-use]").addEventListener("click", () => {
      backgroundSelect.value = item.key;
      updateControlLabels();
      saveGeneratorState();
      render();
    });
    const deleteButton = card.querySelector("[data-delete]");
    card.querySelector("[data-rename]")?.addEventListener("click", () => renameAsset(item.id, item.label));
    if (deleteButton) {
      deleteButton.addEventListener("click", () => deleteAssetWithConfirm("background", item.id, item.label));
    }
    grid.appendChild(card);
  }
}

function renderCharacterManager(content) {
  const items = characterItems();
  content.innerHTML = `
    <div class="asset-actions">
      <button type="button" data-add-character>添加角色</button>
    </div>
    <div class="asset-grid"></div>
    <div class="asset-footer-actions">
      <button type="button" data-add-character>添加角色</button>
      <button type="button" data-scroll-top>回到顶部</button>
    </div>
  `;
  content.querySelectorAll("[data-add-character]").forEach((button) => {
    button.addEventListener("click", () => requestAssetImage("character", ""));
  });
  content.querySelector("[data-scroll-top]").addEventListener("click", () => content.scrollTo({ top: 0, behavior: "smooth" }));
  const grid = content.querySelector(".asset-grid");
  for (const item of items) {
    const imageSrc = item.custom ? item.src : `${item.key}/${item.key} (1).png`;
    const card = document.createElement("article");
    card.className = "asset-card character";
    if ((item.id || item.key) === pendingAssetHighlightId) card.classList.add("is-highlighted");
    card.dataset.assetId = item.id || item.key;
    card.innerHTML = `
      <img src="${escapeHtml(imageSrc)}" alt="${escapeHtml(item.name)}" loading="lazy">
      <strong>${escapeHtml(item.name)}</strong>
      <div class="card-actions">
        <button type="button" data-use>使用</button>
        ${item.custom ? '<button type="button" data-rename>改名</button><button type="button" data-name>姓名</button><button type="button" data-delete>删除</button>' : '<button type="button" disabled>内置</button>'}
      </div>
    `;
    card.querySelector("[data-use]").addEventListener("click", () => {
      characterSelect.value = item.key;
      updateEmotionOptions();
      saveGeneratorState();
      render();
    });
    const nameButton = card.querySelector("[data-name]");
    card.querySelector("[data-rename]")?.addEventListener("click", () => renameAsset(item.id, item.name));
    if (nameButton) nameButton.addEventListener("click", () => openNameEditor(item));
    const deleteButton = card.querySelector("[data-delete]");
    if (deleteButton) deleteButton.addEventListener("click", () => deleteAssetWithConfirm("character", item.id, item.name));
    grid.appendChild(card);
  }
}

function renderEmotionManager(content) {
  const character = currentCharacter();
  if (!character.custom) {
    content.innerHTML = `
      <div class="asset-actions">
        <button type="button" data-choose-owner>${escapeHtml(character.name)}</button>
        <button type="button" data-scroll-top>回到顶部</button>
      </div>
      <div class="asset-grid"></div>
    `;
    content.querySelector("[data-scroll-top]").addEventListener("click", () => content.scrollTo({ top: 0, behavior: "smooth" }));
    content.querySelector("[data-choose-owner]").addEventListener("click", () => window.openMaterialPicker("character"));
    const grid = content.querySelector(".asset-grid");
    for (let index = 1; index <= character.emotions; index++) {
      const card = document.createElement("article");
      card.className = "asset-card emotion";
      card.dataset.assetId = `${character.key}-${index}`;
      card.innerHTML = `
        <img src="${character.key}/${character.key} (${index}).png" alt="表情 ${index}">
        <strong>表情 ${index}</strong>
        <div class="card-actions"><button type="button" data-use>使用</button><button type="button" disabled>内置</button></div>
      `;
      card.querySelector("[data-use]").addEventListener("click", () => {
        emotionSelect.value = String(index);
        updateControlLabels();
        saveGeneratorState();
        render();
      });
      grid.appendChild(card);
    }
    return;
  }

  content.innerHTML = `
    <div class="asset-actions">
      <button type="button" data-choose-owner>${escapeHtml(character.name)}</button>
      <button type="button" data-add-emotion>添加表情</button>
    </div>
    <div class="asset-grid"></div>
    <div class="asset-footer-actions">
      <button type="button" data-add-emotion>添加表情</button>
      <button type="button" data-scroll-top>回到顶部</button>
    </div>
  `;
  content.querySelectorAll("[data-add-emotion]").forEach((button) => {
    button.addEventListener("click", () => requestAssetImage("emotion", character.id));
  });
  content.querySelector("[data-choose-owner]").addEventListener("click", () => window.openMaterialPicker("character"));
  content.querySelector("[data-scroll-top]").addEventListener("click", () => content.scrollTo({ top: 0, behavior: "smooth" }));
  const grid = content.querySelector(".asset-grid");
  character.emotionItems.forEach((item, index) => {
    const card = document.createElement("article");
    card.className = "asset-card emotion";
    if ((item.id || `${character.id}-${index}`) === pendingAssetHighlightId) card.classList.add("is-highlighted");
    card.dataset.assetId = item.id || `${character.id}-${index}`;
    card.innerHTML = `
      <img src="${escapeHtml(item.src)}" alt="${escapeHtml(item.label || `表情 ${index + 1}`)}" loading="lazy">
      <strong>${escapeHtml(item.label || `表情 ${index + 1}`)}</strong>
      <div class="card-actions">
        <button type="button" data-use>使用</button>
        <button type="button" data-rename>改名</button>
        <button type="button" data-up aria-label="将表情前移" ${index === 0 ? "disabled" : ""}>前移</button>
        <button type="button" data-down aria-label="将表情后移" ${index === character.emotionItems.length - 1 ? "disabled" : ""}>后移</button>
        <button type="button" data-delete class="${character.emotionItems.length <= 1 ? "is-disabled" : ""}" aria-disabled="${character.emotionItems.length <= 1 ? "true" : "false"}">删除</button>
      </div>
    `;
    card.querySelector("[data-use]").addEventListener("click", () => {
      emotionSelect.value = String(index + 1);
      updateControlLabels();
      saveGeneratorState();
      render();
    });
    card.querySelector("[data-delete]").addEventListener("click", () => {
      if (character.emotionItems.length <= 1) {
        notifyAndroid("至少保留 1 张表情", true);
        return;
      }
      deleteAssetWithConfirm("emotion", item.id, item.label || `表情 ${index + 1}`);
    });
    card.querySelector("[data-rename]").addEventListener("click", () => renameAsset(item.id, item.label || `表情 ${index + 1}`));
    card.querySelector("[data-up]").addEventListener("click", () => moveEmotion(item.id, -1));
    card.querySelector("[data-down]").addEventListener("click", () => moveEmotion(item.id, 1));
    grid.appendChild(card);
  });
}

async function renameAsset(id, label) {
  const name = await showConfirmDialog({ title: "修改素材名称", message: "名称", confirmText: "保存", danger: false, inputValue: label });
  if (name === null) return;
  if (!name) { showAppNotice("名称不能为空"); return; }
  try {
    nativeUpdateAsset(id, { label: name });
    reloadAndRefreshSelections();
    renderAssetManager();
    requestRender();
    window.dispatchEvent(new CustomEvent("assetschange"));
    showAppNotice("名称已修改");
  } catch (error) { showAppNotice(error.message || "改名失败，请重试"); }
}

function moveEmotion(id, direction) {
  const character = currentCharacter();
  const items = character.emotionItems.slice();
  const index = items.findIndex((item) => item.id === id);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= items.length) return;
  const selectedId = items[(Number(emotionSelect.value) || 1) - 1]?.id;
  [items[index], items[target]] = [items[target], items[index]];
  try {
    items.forEach((item, position) => nativeUpdateAsset(item.id, { order: position + 1 }));
    reloadAndRefreshSelections();
    emotionSelect.value = String(items.findIndex((item) => item.id === selectedId) + 1);
    saveGeneratorState({ emotion: emotionSelect.value });
    pendingAssetScrollId = id;
    renderAssetManager();
    requestRender();
    window.dispatchEvent(new CustomEvent("assetschange"));
  } catch (error) { showAppNotice(error.message || "排序失败，请重试"); }
}

async function deleteAssetWithConfirm(kind, id, label) {
  const ok = await showConfirmDialog({
    title: "删除素材",
    message: `确定删除「${label}」吗？此操作不能撤销。`,
    confirmText: "删除",
    cancelText: "取消",
    danger: true,
  });
  if (!ok) return;
  try { nativeDeleteAsset(kind, id); }
  catch (error) { showAppNotice(error.message || "删除失败，请重试"); return; }
  reloadAndRefreshSelections();
  renderAssetManager();
  await render();
  window.dispatchEvent(new CustomEvent("assetschange"));
  showAppNotice(`已删除「${label}」`);
}

function reloadAndRefreshSelections(preferredCharacter = characterSelect.value, preferredBackground = backgroundSelect.value) {
  reloadAssets();
  refreshCharacterOptions(preferredCharacter);
  refreshBackgroundOptions(preferredBackground);
  updateEmotionOptions(emotionSelect.value);
  saveGeneratorState();
}

const EDITOR_BACKGROUND_SIZE = { width: 1000, height: 326 };
const EDITOR_ROLE_SIZE = { width: 720, height: 700 };

function editorCanvasSize(state) {
  return state && state.kind === "background" ? EDITOR_BACKGROUND_SIZE : EDITOR_ROLE_SIZE;
}

function editorMinScale(state) {
  if (!state || !state.image) return 1;
  const size = editorCanvasSize(state);
  const fit = state.kind === "background" ? Math.max : Math.min;
  return fit(size.width / state.image.naturalWidth, size.height / state.image.naturalHeight) * (state.kind === "background" ? 1 : 0.5);
}

function setEditorCanvasSize(editor, state) {
  const canvasElement = editor.querySelector("canvas");
  const size = editorCanvasSize(state);
  canvasElement.width = size.width;
  canvasElement.height = size.height;
}

function syncEditorZoomRange(editor, state) {
  if (!state || !state.image) return;
  const zoom = editor.querySelector("[data-zoom]");
  const minPercent = Math.ceil(editorMinScale(state) * 100);
  const maxPercent = Math.max(300, minPercent);
  zoom.min = String(minPercent);
  zoom.max = String(maxPercent);
  zoom.value = String(Math.round(state.scale * 100));
}

function clampEditorTransform(state) {
  if (!state || !state.image) return;
  const size = editorCanvasSize(state);
  const minScale = editorMinScale(state);
  state.scale = Math.max(minScale, Number(state.scale) || minScale);
  const scaledWidth = state.image.naturalWidth * state.scale;
  const scaledHeight = state.image.naturalHeight * state.scale;
  if (state.kind !== "background") {
    const visibleWidth = Math.min(scaledWidth, size.width * 0.15);
    const visibleHeight = Math.min(scaledHeight, size.height * 0.15);
    state.x = Math.max(visibleWidth - scaledWidth, Math.min(size.width - visibleWidth, Number(state.x) || 0));
    state.y = Math.max(visibleHeight - scaledHeight, Math.min(size.height - visibleHeight, Number(state.y) || 0));
    return;
  }
  state.x = scaledWidth <= size.width
    ? (size.width - scaledWidth) / 2
    : Math.min(0, Math.max(size.width - scaledWidth, state.x));
  state.y = scaledHeight <= size.height
    ? (size.height - scaledHeight) / 2
    : Math.min(0, Math.max(size.height - scaledHeight, state.y));
}

function drawCheckerboard(context, width, height) {
  const cell = 24;
  context.fillStyle = "#0e0f13";
  context.fillRect(0, 0, width, height);
  context.fillStyle = "rgba(255, 255, 255, 0.08)";
  for (let y = 0; y < height; y += cell) {
    for (let x = 0; x < width; x += cell) {
      if (((x / cell) + (y / cell)) % 2 === 0) {
        context.fillRect(x, y, cell, cell);
      }
    }
  }
}

function openImageEditor(kind, ownerId, dataUrl) {
  const editor = ensureCropEditor();
  const state = {
    kind,
    ownerId,
    dataUrl,
    image: null,
    scale: kind === "background" ? 1 : 0.75,
    x: 0,
    y: 0,
    dragging: false,
    lastX: 0,
    lastY: 0,
  };
  editor.state = state;
  editor.querySelector("[data-editor-save]").disabled = false;
  setEditorCanvasSize(editor, state);
  editor.querySelector("[data-editor-title]").textContent = kind === "background" ? "裁切背景" : "定位角色/表情";
  editor.querySelector("[data-editor-name]").value = kind === "background"
    ? `自定义背景 ${assetState.backgrounds.length + 1}`
    : kind === "character"
      ? `自定义角色 ${assetState.characters.length + 1}`
      : `表情 ${assetState.emotions.filter((item) => item.ownerId === ownerId).length + 1}`;
  openOverlay(editor, "crop");
  loadStandaloneImage(dataUrl).then((img) => {
    if (editor.state !== state || !editor.classList.contains("open")) return;
    state.image = img;
    state.scale = editorMinScale(state) * (kind === "background" ? 1 : 2);
    const size = editorCanvasSize(state);
    state.x = (size.width - img.naturalWidth * state.scale) / 2;
    state.y = (size.height - img.naturalHeight * state.scale) / 2;
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  }).catch(() => { if (editor.state === state) { showAppNotice("无法读取这张图片，请重新选择"); closeOverlay(editor); } });
}

function ensureCropEditor() {
  let editor = document.querySelector(".crop-editor");
  if (editor) return editor;
  editor = document.createElement("section");
  editor.className = "crop-editor";
  editor.innerHTML = `
    <div class="asset-header">
      <strong data-editor-title>裁切</strong>
      <div class="header-actions"><button type="button" data-editor-close>取消</button><button type="button" data-editor-save class="primary">保存</button></div>
    </div>
    <div class="editor-stage"><canvas width="1000" height="326"></canvas></div>
    <div class="editor-controls">
      <input data-editor-name aria-label="素材名称" maxlength="80" />
      <label>缩放 <input data-zoom type="range" min="20" max="300" value="100" /></label>
      <button type="button" data-editor-reset aria-label="重置图片位置和缩放"><i data-lucide="rotate-ccw"></i>重置</button>
    </div>
  `;
  document.body.appendChild(editor);
  const canvasElement = editor.querySelector("canvas");
  const pointers = new Map();
  let pinch = null;
  function point(event) {
    const rect = canvasElement.getBoundingClientRect();
    return { x: (event.clientX - rect.left) * canvasElement.width / rect.width, y: (event.clientY - rect.top) * canvasElement.height / rect.height };
  }
  function beginPinch() {
    if (pointers.size !== 2 || !editor.state?.image) { pinch = null; return; }
    const [a, b] = Array.from(pointers.values());
    pinch = { distance: Math.max(1, Math.hypot(a.x - b.x, a.y - b.y)), x: (a.x + b.x) / 2, y: (a.y + b.y) / 2,
      scale: editor.state.scale, imageX: editor.state.x, imageY: editor.state.y };
  }
  canvasElement.addEventListener("pointerdown", (event) => {
    const state = editor.state;
    if (!state?.image) return;
    if (!pointers.size) pinch = null;
    pointers.set(event.pointerId, point(event));
    state.dragging = true;
    beginPinch();
    canvasElement.setPointerCapture(event.pointerId);
  });
  canvasElement.addEventListener("pointermove", (event) => {
    const state = editor.state;
    if (!state || !pointers.has(event.pointerId)) return;
    const previous = pointers.get(event.pointerId);
    const next = point(event);
    pointers.set(event.pointerId, next);
    if (pinch && pointers.size === 2) {
      const [a, b] = Array.from(pointers.values());
      const minimum = editorMinScale(state);
      state.scale = Math.max(minimum, Math.min(Math.max(3, minimum * 6), pinch.scale * Math.hypot(a.x - b.x, a.y - b.y) / pinch.distance));
      state.x = (a.x + b.x) / 2 - (pinch.x - pinch.imageX) * state.scale / pinch.scale;
      state.y = (a.y + b.y) / 2 - (pinch.y - pinch.imageY) * state.scale / pinch.scale;
    } else {
      state.x += next.x - previous.x;
      state.y += next.y - previous.y;
    }
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  });
  function endPointer(event) { pointers.delete(event.pointerId); beginPinch(); if (editor.state) editor.state.dragging = pointers.size > 0; }
  canvasElement.addEventListener("pointerup", endPointer);
  canvasElement.addEventListener("pointercancel", endPointer);
  canvasElement.addEventListener("lostpointercapture", endPointer);
  editor.querySelector("[data-zoom]").addEventListener("input", (event) => {
    const state = editor.state;
    if (!state) return;
    if (!state.image) return;
    const next = Number(event.target.value) / 100;
    const size = editorCanvasSize(state);
    state.x = size.width / 2 - (size.width / 2 - state.x) * next / state.scale;
    state.y = size.height / 2 - (size.height / 2 - state.y) * next / state.scale;
    state.scale = next;
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  });
  editor.querySelector("[data-editor-reset]").addEventListener("click", () => {
    const state = editor.state;
    if (!state?.image) return;
    state.scale = editorMinScale(state) * (state.kind === "background" ? 1 : 2);
    const size = editorCanvasSize(state);
    state.x = (size.width - state.image.naturalWidth * state.scale) / 2;
    state.y = (size.height - state.image.naturalHeight * state.scale) / 2;
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  });
  editor.querySelector("[data-editor-close]").addEventListener("click", () => closeOverlay(editor));
  editor.querySelector("[data-editor-save]").addEventListener("click", saveEditorAsset);
  if (window.lucide) window.lucide.createIcons();
  return editor;
}

function drawEditor() {
  const editor = ensureCropEditor();
  const state = editor.state;
  const canvasElement = editor.querySelector("canvas");
  const context = canvasElement.getContext("2d");
  context.clearRect(0, 0, canvasElement.width, canvasElement.height);
  drawCheckerboard(context, canvasElement.width, canvasElement.height);
  if (!state || !state.image) return;
  clampEditorTransform(state);
  context.drawImage(
    state.image,
    state.x,
    state.y,
    state.image.naturalWidth * state.scale,
    state.image.naturalHeight * state.scale
  );
  if (state.kind !== "background") {
    context.strokeStyle = "rgba(255, 133, 147, 0.9)";
    context.lineWidth = 3;
    context.strokeRect(0, 0, canvasElement.width, canvasElement.height);
  }
}

async function saveEditorAsset() {
  const editor = ensureCropEditor();
  const state = editor.state;
  const saveButton = editor.querySelector("[data-editor-save]");
  if (saveButton.disabled) return;
  if (!state || !state.image) {
    notifyAndroid("图片还没有加载完成", true);
    return;
  }
  clampEditorTransform(state);
  drawEditor();
  saveButton.disabled = true;
  const name = editor.querySelector("[data-editor-name]").value.trim() || "自定义素材";
  const output = document.createElement("canvas");
  if (state.kind === "background") {
    output.width = CANVAS_WIDTH;
    output.height = CANVAS_HEIGHT;
    const out = output.getContext("2d");
    out.scale(CANVAS_WIDTH / 1000, CANVAS_HEIGHT / 326);
    out.drawImage(state.image, state.x, state.y, state.image.naturalWidth * state.scale, state.image.naturalHeight * state.scale);
  } else {
    output.width = 720;
    output.height = 700;
    const out = output.getContext("2d");
    out.clearRect(0, 0, output.width, output.height);
    out.drawImage(
      state.image,
      state.x,
      state.y,
      state.image.naturalWidth * state.scale,
      state.image.naturalHeight * state.scale
    );
  }
  let dataUrl;
  try { dataUrl = await canvasToDataUrl(output); }
  catch (_) { saveButton.disabled = false; showAppNotice("无法生成素材图片，请重新选择"); return; }
  const id = uid(state.kind);
  const metadata = {
    id,
    label: name,
    ownerId: state.ownerId || "",
    order: Date.now(),
  };
  if (state.kind === "character") {
    metadata.fit = { x: 0, y: ROLE_TOP, w: 720, h: 700 };
    metadata.nameBlocks = [{ id: uid("name"), text: name, x: 759, y: 73, size: 150, color: "#ffffff" }];
  }
  try {
    if (state.kind === "character") {
      nativeSaveCharacterBundle(
        metadata,
        { id: uid("emotion"), ownerId: id, label: "表情 1", order: 1 },
        dataUrl,
      );
    } else {
      nativeSaveAsset(state.kind, metadata, dataUrl);
    }
  } catch (error) {
    const message = error && error.message ? error.message : "素材保存失败";
    showAppNotice(message);
    notifyAndroid(message, true);
    saveButton.disabled = false;
    return;
  }
  pendingAssetScrollId = id;
  pendingAssetHighlightId = id;
  closeOverlay(editor);
  reloadAndRefreshSelections(state.kind === "character" ? id : characterSelect.value, state.kind === "background" ? id : backgroundSelect.value);
  if (state.kind === "emotion") {
    characterSelect.value = state.ownerId;
    updateEmotionOptions();
    const index = currentCharacter().emotionItems.findIndex((item) => item.id === id);
    emotionSelect.value = String(Math.max(0, index) + 1);
  }
  saveGeneratorState();
  renderAssetManager();
  await render();
  saveButton.disabled = false;
  showAppNotice("素材已保存并已选中");
  notifyAndroid("素材已保存并已选中", false);
}

function openNameEditor(character) {
  const editor = ensureNameEditor();
  editor.characterId = character.id;
  editor.characterName = character.name || "名字";
  editor.blocks = JSON.parse(JSON.stringify(character.nameBlocks || []));
  openOverlay(editor, "name");
  renderNameBlocks();
}

function ensureNameEditor() {
  let editor = document.querySelector(".name-editor");
  if (editor) return editor;
  editor = document.createElement("section");
  editor.className = "name-editor";
  editor.innerHTML = `
    <div class="asset-header">
      <strong>姓名字块</strong>
      <div class="header-actions">
        <button type="button" data-name-close>取消</button>
        <button type="button" data-name-save class="primary">保存</button>
      </div>
    </div>
    <div class="name-stage">
      <div class="name-canvas-wrap"><canvas width="2560" height="834"></canvas></div>
    </div>
    <div class="name-tools">
      <label>文字<input data-name-text aria-label="字块文字" placeholder="文字" maxlength="80" /></label>
      <label>颜色<input data-name-color type="color" value="#ffffff" aria-label="字块颜色" /></label>
      <label>字号<input data-name-size type="number" value="150" min="32" max="240" aria-label="字块字号" /></label>
      <button type="button" data-name-add>添加字块</button>
      <button type="button" data-name-delete>删除字块</button>
      <button type="button" data-name-reset>重置布局</button>
    </div>
  `;
  document.body.appendChild(editor);
  editor.querySelector("[data-name-close]").addEventListener("click", () => closeOverlay(editor));
  editor.querySelector("[data-name-add]").addEventListener("click", () => {
    if (editor.blocks.length >= 32) { showAppNotice("最多添加 32 个字块"); return; }
    editor.blocks.push({ id: uid("name"), text: "名字", x: 759, y: 73, size: 150, color: "#ffffff" });
    editor.selectedId = editor.blocks[editor.blocks.length - 1].id;
    renderNameBlocks();
  });
  editor.querySelector("[data-name-delete]").addEventListener("click", () => {
    editor.blocks = editor.blocks.filter((item) => item.id !== editor.selectedId);
    editor.selectedId = editor.blocks[0] && editor.blocks[0].id;
    renderNameBlocks();
  });
  editor.querySelector("[data-name-reset]").addEventListener("click", () => {
    editor.blocks = [{ id: uid("name"), text: editor.characterName || "名字", x: 759, y: 73, size: 150, color: "#ffffff" }];
    editor.selectedId = editor.blocks[0].id;
    renderNameBlocks();
  });
  editor.querySelector("[data-name-save]").addEventListener("click", async () => {
    try { nativeUpdateAsset(editor.characterId, { nameBlocks: editor.blocks }); }
    catch (error) { showAppNotice(error.message || "保存失败，请重试"); return; }
    pendingAssetScrollId = editor.characterId;
    closeOverlay(editor);
    reloadAndRefreshSelections(editor.characterId);
    renderAssetManager();
    await render();
  });
  for (const selector of ["[data-name-text]", "[data-name-color]", "[data-name-size]"]) {
    editor.querySelector(selector).addEventListener("input", updateSelectedNameBlock);
  }
  return editor;
}

function renderNameBlocks() {
  const editor = ensureNameEditor();
  const wrap = editor.querySelector(".name-canvas-wrap");
  const validIds = new Set(editor.blocks.map((item) => item.id));
  wrap.querySelectorAll(".name-block").forEach((item) => { if (!validIds.has(item.dataset.id)) item.remove(); });
  const selected = editor.blocks.find((item) => item.id === editor.selectedId) || editor.blocks[0];
  editor.selectedId = selected && selected.id;
  for (const block of editor.blocks) {
    let el = Array.from(wrap.querySelectorAll(".name-block")).find((item) => item.dataset.id === block.id);
    if (!el) {
      el = document.createElement("div");
      el.className = "name-block";
      el.dataset.id = block.id;
      el.tabIndex = 0;
      el.setAttribute("role", "button");
      el.addEventListener("pointerdown", startNameDrag);
      el.addEventListener("click", () => selectNameBlock(block.id));
      wrap.appendChild(el);
    }
    el.textContent = block.text;
    el.style.left = `${(block.x / CANVAS_WIDTH) * 100}%`;
    el.style.top = `${(block.y / CANVAS_HEIGHT) * 100}%`;
    el.style.fontSize = `${Math.max(12, (block.size / CANVAS_WIDTH) * wrap.clientWidth)}px`;
    el.style.color = block.color || "#ffffff";
    el.setAttribute("aria-label", `姓名字块：${block.text || "空白"}`);
    el.setAttribute("aria-pressed", String(block.id === editor.selectedId));
  }
  if (selected) selectNameBlock(selected.id);
}

function selectNameBlock(id) {
  const editor = ensureNameEditor();
  editor.selectedId = id;
  const block = editor.blocks.find((item) => item.id === id);
  if (!block) return;
  editor.querySelector("[data-name-text]").value = block.text || "";
  editor.querySelector("[data-name-color]").value = block.color || "#ffffff";
  editor.querySelector("[data-name-size]").value = block.size || 150;
}

function updateSelectedNameBlock() {
  const editor = ensureNameEditor();
  const block = editor.blocks.find((item) => item.id === editor.selectedId);
  if (!block) return;
  block.text = editor.querySelector("[data-name-text]").value.slice(0, 80);
  block.color = editor.querySelector("[data-name-color]").value;
  block.size = Math.min(240, Math.max(32, Number(editor.querySelector("[data-name-size]").value) || 150));
  renderNameBlocks();
}

function startNameDrag(event) {
  event.preventDefault();
  const editor = ensureNameEditor();
  const id = event.currentTarget.dataset.id;
  selectNameBlock(id);
  const block = editor.blocks.find((item) => item.id === id);
  const wrap = editor.querySelector(".name-canvas-wrap");
  const startX = Number(block.x) || 0;
  const startY = Number(block.y) || 0;
  const downX = event.clientX;
  const downY = event.clientY;
  const pointerId = event.pointerId;
  const draggedElement = event.currentTarget;
  const move = (moveEvent) => {
    if (moveEvent.pointerId !== pointerId) return;
    const rect = wrap.getBoundingClientRect();
    const box = draggedElement;
    const width = box ? box.getBoundingClientRect().width / rect.width * CANVAS_WIDTH : 32;
    const height = box ? box.getBoundingClientRect().height / rect.height * CANVAS_HEIGHT : 32;
    block.x = Math.max(0, Math.min(CANVAS_WIDTH - width, startX + ((moveEvent.clientX - downX) / rect.width) * CANVAS_WIDTH));
    block.y = Math.max(0, Math.min(CANVAS_HEIGHT - height, startY + ((moveEvent.clientY - downY) / rect.height) * CANVAS_HEIGHT));
    renderNameBlocks();
  };
  const up = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
    window.removeEventListener("pointercancel", up);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
  window.addEventListener("pointercancel", up);
}

async function shareImage() {
  if (shareBusy) { showAppNotice("上一张还在处理"); return; }
  shareBusy = true;
  try {
    const dataUrl = await window.exportImageDataUrl(false);

    if (window.AndroidBridge && typeof window.AndroidBridge.shareImage === "function") {
      window.AndroidBridge.shareImage(dataUrl);
      return;
    }
    const blob = dataUrlToBlob(dataUrl);
    const file = new File([blob], `魔裁文本框-${Date.now()}.png`, { type: "image/png" });
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      await navigator.share({ files: [file], title: "魔裁文本框" });
      return;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = file.name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) {
    if (error.name !== "AbortError") showAppNotice(`分享失败：${error.message}`);
  } finally {
    shareBusy = false;
  }
}

async function shareToQQ() {
  if (!window.AndroidBridge || typeof window.AndroidBridge.shareToQQ !== "function") return shareImage();
  if (shareBusy) { showAppNotice("上一张还在处理"); return; }
  shareBusy = true;
  const waitForImeResult = document.documentElement.classList.contains("ime-mode");
  if (waitForImeResult) window.setImeSendState?.("busy", "正在生成图片…");
  let handedOff = false;
  try {
    const dataUrl = await window.exportImageDataUrl(true);
    window.AndroidBridge.shareToQQ(dataUrl);
    handedOff = waitForImeResult;
  } catch (error) {
    shareBusy = false;
    if (waitForImeResult) window.setImeSendState?.("error", `图片生成失败：${error.message}`);
    showAppNotice(`发到 QQ 失败：${error.message}`);
  } finally {
    if (!handedOff) shareBusy = false;
  }
}

window.sendCurrentImageToQQ = shareToQQ;

window.setImeSendState = function setImeSendState(state, message) {
  shareBusy = state === "busy";
  window.receiveImeSendState?.(state, message || "");
};

window.exportImageDataUrl = async function exportImageDataUrl(forQQ) {
  // current() notifies the state subscriber only when the persisted state changed.
  // Calling applyGeneratorState directly forces a full control refresh on every send.
  window.MahouState.current();
  finishInput();
  if (pendingRenderFrame) { cancelAnimationFrame(pendingRenderFrame); pendingRenderFrame = 0; }
  const exportCanvas = document.createElement("canvas");
  const width = forQQ ? getQqExportMaxWidth() : SHARE_EXPORT_WIDTH;
  let complete = false;
  for (let attempt = 0; attempt < 3 && !complete; attempt++) {
    complete = await render({ forceDialogText: true, targetCanvas: exportCanvas, targetWidth: width });
  }
  if (!complete) throw new Error("素材未能生成，请重新选择后重试");
  // Keep QQ's latency-sensitive send path on the original direct encoding path.
  // Async Blob + FileReader adds a callback/readback round trip before native send.
  const dataUrl = forQQ ? exportCanvas.toDataURL("image/png") : await canvasToDataUrl(exportCanvas);
  if (lineText.classList.contains("inline-line-text") && document.activeElement === lineText) requestRender();
  return dataUrl;
};

window.receiveAssetsChanged = function receiveAssetsChanged() {
  reloadAssets();
  applyGeneratorState(window.MahouState.current());
  if (document.querySelector(".asset-modal.open")) renderAssetManager();
  window.dispatchEvent(new Event("assetschange"));
};

window.receivePickedBackground = async function receivePickedBackground(dataUrl) {
  try {
    openImageEditor("background", "", dataUrl);
  } catch (error) {
    notifyAndroid(`添加背景失败：${error.message}`, true);
  }
};

window.receivePickedAsset = async function receivePickedAsset(kind, ownerId, dataUrl) {
  try {
    openImageEditor(kind || "background", ownerId || "", dataUrl);
  } catch (error) {
    notifyAndroid(`添加素材失败：${error.message}`, true);
  }
};

window.receiveAssetTransferResult = function receiveAssetTransferResult(resultJson) {
  try {
    const result = typeof resultJson === "string" ? JSON.parse(resultJson) : resultJson;
    if (!result) return;
    showAppNotice(result.message || "素材操作完成");
    if (result.status === "success" && result.settings) {
      reloadAssets();
      applyBackupSettings(result.settings);
      if (document.querySelector(".asset-modal.open")) renderAssetManager();
    } else if (result.status === "success") {
      reloadAssets();
      reloadAndRefreshSelections();
      if (document.querySelector(".asset-modal.open")) renderAssetManager();
      render();
    }
    updateStorageUsage();
  } catch (error) {
    notifyAndroid(`素材操作结果无效：${error.message}`, true);
  }
};

function startPopupMode() {
  if (window.AndroidBridge && typeof window.AndroidBridge.startPopupMode === "function") {
    window.AndroidBridge.startPopupMode();
    return;
  }
  alert("弹窗模式只在 APK 里可用。");
}

const ACCESSIBLE_ACTIONS = [
  ["popupButton", "popup"],
  ["randomButton", "random"],
  ["addBackgroundButton", "addBackground"],
  ["assetManagerButton", "assetManager"],
  ["qqButton", "qq"],
  ["shareButton", "share"],
  ["aboutButton", "about"],
];

let accessibilityReportPending = false;
let accessibleSequence = 0;
function reportAccessibleActions() {
  accessibilityReportPending = false;
  if (!window.AndroidBridge || typeof window.AndroidBridge.reportAccessibleActions !== "function") return;
  const actions = [];
  const root = activeOverlay() || document.querySelector(".workspace") || document.body;
  for (const element of root.querySelectorAll('button,[role="button"]')) {
    if (element.hidden || element.closest("[inert]")) continue;
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    if (style.display === "none" || style.visibility === "hidden" || rect.width <= 0 || rect.height <= 0) continue;
    const left = Math.max(0, rect.left);
    const top = Math.max(0, rect.top);
    const right = Math.min(window.innerWidth, rect.right);
    const bottom = Math.min(window.innerHeight, rect.bottom);
    if (right <= left || bottom <= top) continue;
    if (!element.id) element.id = `mahou-action-${++accessibleSequence}`;
    actions.push({
      action: `web:${element.id}`,
      label: element.getAttribute("aria-label") || element.textContent.trim(),
      left,
      top,
      right,
      bottom,
      enabled: !element.disabled && element.getAttribute("aria-disabled") !== "true",
    });
  }
  window.AndroidBridge.reportAccessibleActions(JSON.stringify({
    pixelRatio: window.devicePixelRatio || 1,
    actions,
  }));
}

function scheduleAccessibilityReport() {
  if (accessibilityReportPending) return;
  accessibilityReportPending = true;
  requestAnimationFrame(reportAccessibleActions);
}

if (window.AndroidBridge && typeof window.AndroidBridge.reportAccessibleActions === "function") {
  new ResizeObserver(scheduleAccessibilityReport).observe(document.documentElement);
  new MutationObserver(scheduleAccessibilityReport).observe(document.body, {
    attributes: true,
    childList: true,
    subtree: true,
  });
  addEventListener("scroll", scheduleAccessibilityReport, true);
  addEventListener("resize", scheduleAccessibilityReport);
}

if (!window.AndroidBridge && "serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}

addEventListener("resize", requestRender, { passive: true });
setupControls();
render();
scheduleAccessibilityReport();
