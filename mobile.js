const CANVAS_WIDTH = 2560;
const CANVAS_HEIGHT = 834;
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
const BUILT_IN_BACKGROUND_COUNT = 16;
const STORAGE_KEYS = {
  fallbackAssets: "mahou.fallbackAssets",
  lineText: "mahou.lineText",
  character: "mahou.character",
  emotion: "mahou.emotion",
  background: "mahou.background",
  qqExportSize: "mahou.qqExportSize",
  uiTheme: "mahou.uiTheme",
};

let assetState = loadAssets();
let renderToken = 0;
let pendingPick = null;
let currentAssetTab = "background";
let pendingAssetScrollId = "";
let pendingAssetHighlightId = "";
let suppressNextOverlayPop = false;
const isPopupMode = new URLSearchParams(location.search).get("popup") === "1";

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
  console.log(message);
  if (visible && window.AndroidBridge && typeof window.AndroidBridge.showToast === "function") {
    try {
      window.AndroidBridge.showToast(message);
    } catch (error) {
      console.warn("Android toast failed", error);
    }
  }
}

function showAppNotice(message) {
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
  showAppNotice.timer = setTimeout(() => notice.classList.remove("open"), 1800);
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
  };
}

function applyBackupSettings(settings) {
  if (!settings || typeof settings !== "object") return;
  if (typeof settings.lineText === "string") lineText.value = settings.lineText.slice(0, 180);
  if (typeof settings.uiTheme === "string") saveUiThemeKey(settings.uiTheme);
  if (typeof settings.qqExportSize === "string") saveQqExportSizeKey(settings.qqExportSize);
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
    src: item.src,
  })));
}

function customCharacters() {
  return assetState.characters.map((item, index) => {
    const emotions = assetState.emotions
      .filter((emotion) => emotion.ownerId === item.id)
      .sort((a, b) => (a.order || 0) - (b.order || 0));
    return {
      key: item.id,
      id: item.id,
      custom: true,
      name: item.label || `自定义角色 ${index + 1}`,
      emotions: Math.max(1, emotions.length),
      emotionItems: emotions,
      src: item.src,
      fit: item.fit || { x: 0, y: ROLE_TOP, w: 720, h: 700 },
      nameBlocks: item.nameBlocks || [{
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

function saveGeneratorState() {
  try {
    localStorage.setItem(STORAGE_KEYS.lineText, lineText.value);
    localStorage.setItem(STORAGE_KEYS.character, characterSelect.value);
    localStorage.setItem(STORAGE_KEYS.emotion, emotionSelect.value);
    localStorage.setItem(STORAGE_KEYS.background, backgroundSelect.value);
  } catch (error) {
    console.warn("Failed to save state", error);
  }
}

function rgb(values) {
  return `rgb(${values[0]}, ${values[1]}, ${values[2]})`;
}

function loadImage(src) {
  if (!imageCache.has(src)) {
    imageCache.set(src, new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error(`无法加载图片：${src}`));
      img.src = src;
    }));
  }
  return imageCache.get(src);
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
  render();
}

function stepNumericSelect(select, delta) {
  const options = Array.from(select.options);
  const current = Math.max(0, options.findIndex((option) => option.value === select.value));
  const next = wrapIndex(current, options.length, delta);
  select.value = options[next].value;
  updateControlLabels();
  saveGeneratorState();
  render();
}

function chooseRandom() {
  const character = currentCharacter();
  const backgrounds = backgroundItems();
  emotionSelect.value = String(1 + Math.floor(Math.random() * character.emotions));
  backgroundSelect.value = backgrounds[Math.floor(Math.random() * backgrounds.length)].key;
  updateControlLabels();
  saveGeneratorState();
  render();
}

function refreshCharacterOptions(preferredKey) {
  const items = characterItems();
  fillSelect(characterSelect, items, (item) => item.key, (item) => item.name);
  characterSelect.value = items.some((item) => item.key === preferredKey) ? preferredKey : (items.some((item) => item.key === characterSelect.value) ? characterSelect.value : "sherri");
}

function setupControls() {
  const savedCharacter = localStorage.getItem(STORAGE_KEYS.character);
  const savedEmotion = localStorage.getItem(STORAGE_KEYS.emotion);
  const savedBackground = localStorage.getItem(STORAGE_KEYS.background);
  const savedText = localStorage.getItem(STORAGE_KEYS.lineText);

  if (savedText !== null) {
    lineText.value = savedText;
  }

  refreshCharacterOptions(savedCharacter);
  refreshBackgroundOptions(savedBackground);
  updateEmotionOptions(savedEmotion);

  characterSelect.addEventListener("change", () => {
    updateEmotionOptions();
    saveGeneratorState();
    render();
  });
  emotionSelect.addEventListener("change", () => {
    saveGeneratorState();
    render();
  });
  backgroundSelect.addEventListener("change", () => {
    updateControlLabels();
    saveGeneratorState();
    render();
  });
  characterPrev.addEventListener("click", () => stepCharacter(-1));
  characterNext.addEventListener("click", () => stepCharacter(1));
  emotionPrev.addEventListener("click", () => stepNumericSelect(emotionSelect, -1));
  emotionNext.addEventListener("click", () => stepNumericSelect(emotionSelect, 1));
  backgroundPrev.addEventListener("click", () => stepNumericSelect(backgroundSelect, -1));
  backgroundNext.addEventListener("click", () => stepNumericSelect(backgroundSelect, 1));
  lineText.addEventListener("input", () => {
    saveGeneratorState();
    render();
  });
  setupKeyboardDoneButton();
  popupButton.addEventListener("click", startPopupMode);
  randomButton.addEventListener("click", chooseRandom);
  addBackgroundButton.addEventListener("click", addBackground);
  assetManagerButton.addEventListener("click", openAssetManager);
  aboutButton.addEventListener("click", openAboutModal);
  qqButton.addEventListener("click", shareToQQ);
  shareButton.addEventListener("click", shareImage);
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
  if (isPopupMode) {
    popupButton.setAttribute("hidden", "hidden");
  }
  updateControlLabels();
}

function setupKeyboardDoneButton() {
  if (document.querySelector("[data-keyboard-done]")) return;
  const button = document.createElement("button");
  button.type = "button";
  button.className = "keyboard-done";
  button.dataset.keyboardDone = "true";
  button.textContent = "完成输入";
  button.setAttribute("aria-label", "收起键盘并完成输入");
  button.addEventListener("click", () => {
    lineText.blur();
    render();
  });
  lineText.insertAdjacentElement("afterend", button);
}

function setTextFont(size) {
  ctx.font = `${size}px MahouFont, "PingFang SC", "Microsoft YaHei", sans-serif`;
  ctx.textBaseline = "top";
}

function wrapParagraph(paragraph, maxWidth) {
  const units = paragraph.includes(" ") ? paragraph.split(/(\s+)/) : Array.from(paragraph);
  const lines = [];
  let buffer = "";

  for (const unit of units) {
    const trial = buffer + unit;
    if (ctx.measureText(trial).width <= maxWidth) {
      buffer = trial;
      continue;
    }

    if (buffer.trim().length > 0 || !paragraph.includes(" ")) {
      lines.push(buffer.trimEnd());
    }
    buffer = unit.trimStart();

    while (ctx.measureText(buffer).width > maxWidth && buffer.length > 1) {
      let slice = "";
      for (const char of Array.from(buffer)) {
        if (ctx.measureText(slice + char).width > maxWidth) break;
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

function wrapText(text, fontSize, maxWidth) {
  setTextFont(fontSize);
  return text.split(/\r?\n/).flatMap((paragraph) => wrapParagraph(paragraph, maxWidth));
}

function measureBlock(lines, fontSize) {
  setTextFont(fontSize);
  const lineHeight = Math.round(fontSize * 1.22);
  const width = Math.max(0, ...lines.map((line) => ctx.measureText(line).width));
  return { width, height: Math.max(lineHeight, lines.length * lineHeight), lineHeight };
}

function fitText(text, maxWidth, maxHeight) {
  let lo = 12;
  let hi = 145;
  let best = { size: 12, lines: wrapText(text, 12, maxWidth), lineHeight: 15, height: 15 };

  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    const lines = wrapText(text, mid, maxWidth);
    const block = measureBlock(lines, mid);
    if (block.width <= maxWidth && block.height <= maxHeight) {
      best = { size: mid, lines, lineHeight: block.lineHeight, height: block.height };
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return best;
}

function drawSegmentedText(text, x, y, baseColor, bracketColor) {
  let activeBracket = false;
  let cursor = x;

  for (const char of Array.from(text)) {
    if (char === "[" || char === "【") {
      activeBracket = true;
    }

    const color = activeBracket ? bracketColor : baseColor;
    ctx.fillStyle = "rgba(0, 0, 0, 0.82)";
    ctx.fillText(char, cursor + 4, y + 4);
    ctx.fillStyle = color;
    ctx.fillText(char, cursor, y);
    cursor += ctx.measureText(char).width;

    if (char === "]" || char === "】") {
      activeBracket = false;
    }
  }
}

function drawDialogText(character) {
  const [x1, y1] = TEXT_TOP_LEFT;
  const [x2, y2] = TEXT_BOTTOM_RIGHT;
  const text = lineText.value.trim() || " ";
  const fitted = fitText(text, x2 - x1, y2 - y1);
  const bracketColor = character.key === "anan" ? rgb(character.color) : "rgb(137, 177, 251)";
  setTextFont(fitted.size);

  let y = y1;
  for (const line of fitted.lines) {
    drawSegmentedText(line, x1, y, "rgb(255, 255, 255)", bracketColor);
    y += fitted.lineHeight;
    if (y > y2) break;
  }
}

function drawCharacterName(character) {
  if (character.custom) {
    for (const block of character.nameBlocks || []) {
      const text = block.text || "";
      if (!text) continue;
      const size = Number(block.size) || 120;
      ctx.font = `${size}px MahouFont, "PingFang SC", "Microsoft YaHei", sans-serif`;
      ctx.textBaseline = "top";
      ctx.fillStyle = "rgb(0, 0, 0)";
      ctx.fillText(text, Number(block.x) + 3, Number(block.y) + 3);
      ctx.fillStyle = block.color || "#ffffff";
      ctx.fillText(text, Number(block.x), Number(block.y));
    }
    return;
  }

  for (const [text, x, y, size, color] of character.nameParts) {
    if (!text) continue;
    ctx.font = `${size}px MahouFont, "PingFang SC", "Microsoft YaHei", sans-serif`;
    ctx.textBaseline = "top";
    ctx.fillStyle = "rgb(0, 0, 0)";
    ctx.fillText(text, x + 2, y + 2);
    ctx.fillStyle = color ? rgb(color) : "rgb(255, 255, 255)";
    ctx.fillText(text, x, y);
  }
}

function roleSource(character, emotionIndex) {
  if (!character.custom) {
    return `${character.key}/${character.key} (${emotionIndex}).png`;
  }
  const emotion = currentEmotionItem(character);
  return (emotion && emotion.src) || character.src;
}

function drawRoleImage(role, character) {
  if (!character.custom) {
    ctx.drawImage(role, 0, ROLE_TOP);
    return;
  }
  const fit = character.fit || { x: 0, y: ROLE_TOP, w: 720, h: 700 };
  ctx.drawImage(role, Number(fit.x) || 0, Number(fit.y) || ROLE_TOP, Number(fit.w) || 720, Number(fit.h) || 700);
}

async function render() {
  const token = ++renderToken;
  const character = currentCharacter();
  const backgroundItem = currentBackground();
  const emotionIndex = Number(emotionSelect.value) || 1;

  ctx.clearRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
  ctx.fillStyle = "#111218";
  ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

  try {
    const [backgroundImage, role] = await Promise.all([
      loadImage(backgroundItem.src),
      loadImage(roleSource(character, emotionIndex)),
      document.fonts.ready,
    ]);
    if (token !== renderToken) return;
    ctx.drawImage(backgroundImage, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    drawRoleImage(role, character);
    drawDialogText(character);
    drawCharacterName(character);
  } catch (error) {
    ctx.fillStyle = "#fff";
    ctx.font = "48px sans-serif";
    ctx.fillText(error.message, 60, 80);
  }
}

function canvasToBlob() {
  return new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
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

function androidExportDataUrl(exportWidth = SHARE_EXPORT_WIDTH) {
  const width = Math.min(Math.max(1, Math.round(exportWidth)), CANVAS_WIDTH);
  const scale = width / CANVAS_WIDTH;
  const height = Math.round(CANVAS_HEIGHT * scale);
  const exportCanvas = document.createElement("canvas");
  exportCanvas.width = width;
  exportCanvas.height = height;
  const exportContext = exportCanvas.getContext("2d");
  exportContext.drawImage(canvas, 0, 0, width, height);
  return exportCanvas.toDataURL("image/png");
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
  return exportCanvas.toDataURL("image/jpeg", 0.88);
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
  element.classList.add("open");
  if (!element.dataset.historyOpen) {
    element.dataset.historyOpen = historyName;
    history.pushState({ mahouOverlay: historyName }, "", location.href);
  }
}

function closeOverlay(element, syncHistory = true) {
  if (!element || !element.classList.contains("open")) return;
  element.classList.remove("open");
  delete element.dataset.historyOpen;
  if (syncHistory && history.state && history.state.mahouOverlay) {
    suppressNextOverlayPop = true;
    history.back();
  }
}

function closeTopOverlayFromHistory() {
  const aboutModal = document.querySelector(".about-modal.open");
  if (aboutModal) {
    closeOverlay(aboutModal, false);
    return true;
  }
  const nameEditor = document.querySelector(".name-editor.open");
  if (nameEditor) {
    closeOverlay(nameEditor, false);
    return true;
  }
  const cropEditor = document.querySelector(".crop-editor.open");
  if (cropEditor) {
    closeOverlay(cropEditor, false);
    return true;
  }
  const assetModal = document.querySelector(".asset-modal.open");
  if (assetModal) {
    closeOverlay(assetModal, false);
    return true;
  }
  return false;
}

window.handleAndroidBack = function handleAndroidBack() {
  const confirmDialog = document.querySelector(".confirm-dialog.open");
  if (confirmDialog) {
    confirmDialog.classList.remove("open");
    return true;
  }
  if (closeTopOverlayFromHistory()) {
    return true;
  }
  const nameEditor = document.querySelector(".name-editor.open");
  if (nameEditor) {
    closeOverlay(nameEditor, false);
    return true;
  }
  const cropEditor = document.querySelector(".crop-editor.open");
  if (cropEditor) {
    closeOverlay(cropEditor, false);
    return true;
  }
  const assetModal = document.querySelector(".asset-modal.open");
  if (assetModal) {
    closeOverlay(assetModal, false);
    return true;
  }
  return false;
};

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

function showConfirmDialog({ title, message, confirmText = "删除", cancelText = "取消", danger = true }) {
  return new Promise((resolve) => {
    let dialog = document.querySelector(".confirm-dialog");
    if (!dialog) {
      dialog = document.createElement("section");
      dialog.className = "confirm-dialog";
      dialog.innerHTML = `
        <div class="confirm-card" role="dialog" aria-modal="true">
          <strong data-confirm-title></strong>
          <p data-confirm-message></p>
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
    dialog.querySelector("[data-confirm-cancel]").textContent = cancelText;
    const ok = dialog.querySelector("[data-confirm-ok]");
    ok.textContent = confirmText;
    ok.classList.toggle("danger", danger);
    dialog.classList.add("open");
    const finish = (value) => {
      dialog.classList.remove("open");
      dialog.querySelector("[data-confirm-cancel]").onclick = null;
      ok.onclick = null;
      resolve(value);
    };
    dialog.querySelector("[data-confirm-cancel]").onclick = () => finish(false);
    ok.onclick = () => finish(true);
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
      <p>台词、自定义背景、角色和表情只保存在本机。应用不联网，也不会上传素材；只有你主动分享时，生成的图片才会交给所选目标应用。</p>
      <h2>权限用途</h2>
      <p>悬浮窗权限用于在 QQ 等应用上方显示生成器；通知权限用于提示悬浮窗正在运行，并提供恢复和关闭入口；文件选择器仅在你导入、备份或恢复素材时打开。</p>
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
  if (pendingAssetScrollId) {
    scrollAssetIntoView(pendingAssetScrollId);
    pendingAssetScrollId = "";
  }
  updateStorageUsage();
}

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
      <img src="${item.src}" alt="${item.label}">
      <strong>${item.label}</strong>
      <div class="card-actions">
        <button type="button" data-use>使用</button>
        ${item.custom ? '<button type="button" data-delete>删除</button>' : '<button type="button" disabled>内置</button>'}
      </div>
    `;
    card.querySelector("[data-use]").addEventListener("click", () => {
      backgroundSelect.value = item.key;
      updateControlLabels();
      saveGeneratorState();
      render();
    });
    const deleteButton = card.querySelector("[data-delete]");
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
      <img src="${imageSrc}" alt="${item.name}">
      <strong>${item.name}</strong>
      <div class="card-actions">
        <button type="button" data-use>使用</button>
        ${item.custom ? '<button type="button" data-name>姓名</button><button type="button" data-delete>删除</button>' : '<button type="button" disabled>内置</button>'}
      </div>
    `;
    card.querySelector("[data-use]").addEventListener("click", () => {
      characterSelect.value = item.key;
      updateEmotionOptions();
      saveGeneratorState();
      render();
    });
    const nameButton = card.querySelector("[data-name]");
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
        <span>${character.name}</span>
        <button type="button" data-scroll-top>回到顶部</button>
      </div>
      <div class="asset-grid"></div>
    `;
    content.querySelector("[data-scroll-top]").addEventListener("click", () => content.scrollTo({ top: 0, behavior: "smooth" }));
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
      <span>${character.name}</span>
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
  content.querySelector("[data-scroll-top]").addEventListener("click", () => content.scrollTo({ top: 0, behavior: "smooth" }));
  const grid = content.querySelector(".asset-grid");
  character.emotionItems.forEach((item, index) => {
    const card = document.createElement("article");
    card.className = "asset-card emotion";
    if ((item.id || `${character.id}-${index}`) === pendingAssetHighlightId) card.classList.add("is-highlighted");
    card.dataset.assetId = item.id || `${character.id}-${index}`;
    card.innerHTML = `
      <img src="${item.src}" alt="${item.label || `表情 ${index + 1}`}">
      <strong>${item.label || `表情 ${index + 1}`}</strong>
      <div class="card-actions">
        <button type="button" data-use>使用</button>
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
    grid.appendChild(card);
  });
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
  nativeDeleteAsset(kind, id);
  reloadAndRefreshSelections();
  renderAssetManager();
  await render();
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
  return Math.max(size.width / state.image.naturalWidth, size.height / state.image.naturalHeight);
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
  setEditorCanvasSize(editor, state);
  editor.querySelector("[data-editor-title]").textContent = kind === "background" ? "裁切背景" : "定位角色/表情";
  editor.querySelector("[data-editor-name]").value = kind === "background"
    ? `自定义背景 ${assetState.backgrounds.length + 1}`
    : kind === "character"
      ? `自定义角色 ${assetState.characters.length + 1}`
      : `表情 ${assetState.emotions.filter((item) => item.ownerId === ownerId).length + 1}`;
  openOverlay(editor, "crop");
  loadStandaloneImage(dataUrl).then((img) => {
    state.image = img;
    state.scale = editorMinScale(state);
    const size = editorCanvasSize(state);
    state.x = (size.width - img.naturalWidth * state.scale) / 2;
    state.y = (size.height - img.naturalHeight * state.scale) / 2;
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  });
}

function ensureCropEditor() {
  let editor = document.querySelector(".crop-editor");
  if (editor) return editor;
  editor = document.createElement("section");
  editor.className = "crop-editor";
  editor.innerHTML = `
    <div class="asset-header">
      <strong data-editor-title>裁切</strong>
      <button type="button" data-editor-close>取消</button>
    </div>
    <div class="editor-stage"><canvas width="1000" height="326"></canvas></div>
    <div class="editor-controls">
      <div class="editor-hint">拖动图片调整位置；棋盘格代表透明或空白区域。</div>
      <input data-editor-name aria-label="素材名称" />
      <label>缩放 <input data-zoom type="range" min="20" max="300" value="100" /></label>
      <div class="editor-actions">
        <button type="button" data-editor-save class="primary">保存</button>
      </div>
    </div>
  `;
  document.body.appendChild(editor);
  const canvasElement = editor.querySelector("canvas");
  canvasElement.addEventListener("pointerdown", (event) => {
    const state = editor.state;
    state.dragging = true;
    state.lastX = event.clientX;
    state.lastY = event.clientY;
    canvasElement.setPointerCapture(event.pointerId);
  });
  canvasElement.addEventListener("pointermove", (event) => {
    const state = editor.state;
    if (!state || !state.dragging) return;
    const rect = canvasElement.getBoundingClientRect();
    const sx = canvasElement.width / rect.width;
    const sy = canvasElement.height / rect.height;
    state.x += (event.clientX - state.lastX) * sx;
    state.y += (event.clientY - state.lastY) * sy;
    state.lastX = event.clientX;
    state.lastY = event.clientY;
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  });
  canvasElement.addEventListener("pointerup", () => {
    if (editor.state) editor.state.dragging = false;
  });
  editor.querySelector("[data-zoom]").addEventListener("input", (event) => {
    const state = editor.state;
    if (!state) return;
    state.scale = Number(event.target.value) / 100;
    clampEditorTransform(state);
    syncEditorZoomRange(editor, state);
    drawEditor();
  });
  editor.querySelector("[data-editor-close]").addEventListener("click", () => closeOverlay(editor));
  editor.querySelector("[data-editor-save]").addEventListener("click", saveEditorAsset);
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
  if (!state || !state.image) {
    notifyAndroid("图片还没有加载完成", true);
    return;
  }
  clampEditorTransform(state);
  drawEditor();
  const name = editor.querySelector("[data-editor-name]").value.trim() || "自定义素材";
  const output = document.createElement("canvas");
  if (state.kind === "background") {
    output.width = CANVAS_WIDTH;
    output.height = CANVAS_HEIGHT;
    output.getContext("2d").drawImage(editor.querySelector("canvas"), 0, 0, output.width, output.height);
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
  const dataUrl = output.toDataURL("image/png");
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
    return;
  }
  pendingAssetScrollId = id;
  pendingAssetHighlightId = id;
  closeOverlay(editor);
  reloadAndRefreshSelections(state.kind === "character" ? id : characterSelect.value, state.kind === "background" ? id : backgroundSelect.value);
  renderAssetManager();
  await render();
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
      <input data-name-text aria-label="字块文字" placeholder="文字" />
      <input data-name-color type="color" value="#ffffff" aria-label="字块颜色" />
      <input data-name-size type="number" value="150" min="20" max="260" aria-label="字块字号" />
      <button type="button" data-name-add>添加字块</button>
      <button type="button" data-name-delete>删除字块</button>
      <button type="button" data-name-reset>重置布局</button>
    </div>
  `;
  document.body.appendChild(editor);
  editor.querySelector("[data-name-close]").addEventListener("click", () => closeOverlay(editor));
  editor.querySelector("[data-name-add]").addEventListener("click", () => {
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
    nativeUpdateAsset(editor.characterId, { nameBlocks: editor.blocks });
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
  wrap.querySelectorAll(".name-block").forEach((item) => item.remove());
  const selected = editor.blocks.find((item) => item.id === editor.selectedId) || editor.blocks[0];
  editor.selectedId = selected && selected.id;
  for (const block of editor.blocks) {
    const el = document.createElement("div");
    el.className = "name-block";
    el.textContent = block.text;
    el.style.left = `${(block.x / CANVAS_WIDTH) * 100}%`;
    el.style.top = `${(block.y / CANVAS_HEIGHT) * 100}%`;
    el.style.fontSize = `${Math.max(12, (block.size / CANVAS_WIDTH) * wrap.clientWidth)}px`;
    el.style.color = block.color || "#ffffff";
    el.dataset.id = block.id;
    el.addEventListener("pointerdown", startNameDrag);
    el.addEventListener("click", () => selectNameBlock(block.id));
    wrap.appendChild(el);
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
  block.text = editor.querySelector("[data-name-text]").value;
  block.color = editor.querySelector("[data-name-color]").value;
  block.size = Number(editor.querySelector("[data-name-size]").value) || 150;
  renderNameBlocks();
}

function startNameDrag(event) {
  const editor = ensureNameEditor();
  const id = event.currentTarget.dataset.id;
  selectNameBlock(id);
  const block = editor.blocks.find((item) => item.id === id);
  const wrap = editor.querySelector(".name-canvas-wrap");
  const move = (moveEvent) => {
    const rect = wrap.getBoundingClientRect();
    block.x = Math.max(0, Math.min(CANVAS_WIDTH, ((moveEvent.clientX - rect.left) / rect.width) * CANVAS_WIDTH));
    block.y = Math.max(0, Math.min(CANVAS_HEIGHT, ((moveEvent.clientY - rect.top) / rect.height) * CANVAS_HEIGHT));
    renderNameBlocks();
  };
  const up = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
}

async function shareImage() {
  try {
    lineText.blur();
    await render();

    if (window.AndroidBridge && typeof window.AndroidBridge.shareImage === "function") {
      const dataUrl = androidExportDataUrl(SHARE_EXPORT_WIDTH);
      window.AndroidBridge.shareImage(dataUrl);
      return;
    }
  } catch (error) {
    notifyAndroid(`分享失败：${error.message}`, true);
    return;
  }

  const blob = await canvasToBlob();
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
  if (/iPhone|iPad|iPod/i.test(navigator.userAgent)) {
    window.open(url, "_blank", "noopener");
  }
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function shareToQQ() {
  try {
    lineText.blur();
    await render();

    if (window.AndroidBridge && typeof window.AndroidBridge.shareToQQ === "function") {
      const dataUrl = androidExportDataUrl(getQqExportMaxWidth());
      window.AndroidBridge.shareToQQ(dataUrl);
      return;
    }
  } catch (error) {
    notifyAndroid(`发到 QQ 失败：${error.message}`, true);
    return;
  }

  await shareImage();
}

window.exportImageDataUrl = async function exportImageDataUrl(forQQ) {
  await render();
  const width = forQQ ? getQqExportMaxWidth() : SHARE_EXPORT_WIDTH;
  return androidExportDataUrl(width);
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
function reportAccessibleActions() {
  accessibilityReportPending = false;
  if (!window.AndroidBridge || typeof window.AndroidBridge.reportAccessibleActions !== "function") return;
  const actions = [];
  for (const [id, action] of ACCESSIBLE_ACTIONS) {
    const element = document.getElementById(id);
    if (!element || element.hidden) continue;
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    if (style.display === "none" || style.visibility === "hidden" || rect.width <= 0 || rect.height <= 0) continue;
    const left = Math.max(0, rect.left);
    const top = Math.max(0, rect.top);
    const right = Math.min(window.innerWidth, rect.right);
    const bottom = Math.min(window.innerHeight, rect.bottom);
    if (right <= left || bottom <= top) continue;
    actions.push({
      action,
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

setupControls();
render();
scheduleAccessibilityReport();
