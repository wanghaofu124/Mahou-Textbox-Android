(function () {
  "use strict";
  let pickerKind = "character";
  let pickerScope = "all";
  let pickerCatalog = "";
  let pickerButtons = new Map();

  function icons() {
    if (window.lucide) window.lucide.createIcons({ attrs: { "aria-hidden": "true", focusable: "false" } });
  }

  function thumb(id, source) {
    const image = document.getElementById(id);
    if (image && image.getAttribute("src") !== source) image.src = source;
  }

  function syncWorkspace(event) {
    const changedFields = event && event.detail && event.detail.changedFields;
    if (Array.isArray(changedFields) && changedFields.length === 1 && changedFields[0] === "text") {
      const counter = document.getElementById("textCounter");
      if (counter) counter.textContent = `${lineText.value.length} / 180`;
      return;
    }
    const character = currentCharacter();
    const emotion = Number(emotionSelect.value) || 1;
    thumb("currentCharacterThumb", character.custom ? character.src : `${character.key}/${character.key} (1).png`);
    thumb("currentEmotionThumb", roleSource(character, emotion));
    thumb("currentBackgroundThumb", currentBackground().src);
    const counter = document.getElementById("textCounter");
    if (counter) counter.textContent = `${lineText.value.length} / 180`;
    document.getElementById("pickCharacter")?.classList.toggle("is-custom", Boolean(character.custom));
    document.getElementById("pickEmotion")?.classList.toggle("is-custom", Boolean(character.custom));
    document.getElementById("pickBackground")?.classList.toggle("is-custom", Boolean(currentBackground().custom));
    document.getElementById("pickCharacter")?.setAttribute("aria-label", `选择角色，当前${character.name}`);
    document.getElementById("pickEmotion")?.setAttribute("aria-label", `选择表情，当前${emotionLabel.textContent}`);
    document.getElementById("pickBackground")?.setAttribute("aria-label", `选择背景，当前${backgroundLabel.textContent}`);
    const summary = document.getElementById("catalogSummary");
    if (summary) {
      const customCount = characterItems().filter((item) => item.custom).length + backgroundItems().filter((item) => item.custom).length;
      summary.textContent = customCount
        ? `内置 ${characters.length} · 自定义 ${customCount}`
        : `内置角色 ${characters.length} · 背景 ${builtInBackgrounds().length}`;
    }
    if (document.querySelector(".selection-sheet.open")) renderPicker();
  }

  function pickerItems() {
    if (pickerKind === "character") return characterItems().map((item) => ({
      key: item.key, label: item.name, custom: Boolean(item.custom),
      src: item.custom ? item.src : `${item.key}/${item.key} (1).png`,
    }));
    if (pickerKind === "background") return backgroundItems().map((item) => ({ ...item, custom: Boolean(item.custom) }));
    const character = currentCharacter();
    return Array.from({ length: character.emotions }, (_, index) => ({
      key: String(index + 1),
      custom: Boolean(character.custom),
      label: character.custom ? (character.emotionItems[index]?.label || `表情 ${index + 1}`) : `表情 ${index + 1}`,
      src: character.custom ? (character.emotionItems[index]?.src || character.src) : `${character.key}/${character.key} (${index + 1}).png`,
    }));
  }

  function ensurePicker() {
    let sheet = document.querySelector(".selection-sheet");
    if (sheet) return sheet;
    sheet = document.createElement("section");
    sheet.className = "selection-sheet";
    sheet.setAttribute("aria-label", "选择素材");
    sheet.innerHTML = `
      <div class="asset-header"><strong data-picker-title>选择素材</strong><button type="button" data-picker-close class="icon-button" aria-label="关闭素材选择" title="关闭"><i data-lucide="x"></i></button></div>
      <div class="picker-tabs" role="tablist">
        <button type="button" role="tab" data-picker-kind="character">角色</button><button type="button" role="tab" data-picker-kind="emotion">表情</button><button type="button" role="tab" data-picker-kind="background">背景</button>
      </div>
      <div class="picker-scope" role="group" aria-label="素材来源"><button type="button" data-picker-scope="all">全部</button><button type="button" data-picker-scope="builtin">内置</button><button type="button" data-picker-scope="custom">自定义</button></div>
      <div class="picker-search"><input type="search" data-picker-search aria-label="搜索素材" placeholder="搜索名称" /></div>
      <div class="picker-grid" role="group" aria-label="素材总览"></div>`;
    document.body.appendChild(sheet);
    sheet.querySelector("[data-picker-close]").addEventListener("click", () => closeOverlay(sheet));
    sheet.querySelector("[data-picker-search]").addEventListener("input", renderPicker);
    sheet.querySelectorAll("[data-picker-kind]").forEach((button) => button.addEventListener("click", () => {
      pickerKind = button.dataset.pickerKind;
      sheet.querySelector("[data-picker-search]").value = "";
      pickerCatalog = "";
      renderPicker();
      sheet.querySelector(".picker-grid").scrollTop = 0;
    }));
    sheet.querySelectorAll("[data-picker-scope]").forEach((button) => button.addEventListener("click", () => {
      pickerScope = button.dataset.pickerScope;
      renderPicker();
    }));
    icons();
    return sheet;
  }

  function renderPicker() {
    const sheet = ensurePicker();
    const items = pickerItems();
    const catalog = JSON.stringify([pickerKind, items]);
    const current = pickerKind === "character" ? characterSelect.value : pickerKind === "background" ? backgroundSelect.value : emotionSelect.value;
    const query = sheet.querySelector("[data-picker-search]").value.trim().toLocaleLowerCase();
    sheet.querySelector("[data-picker-title]").textContent = { character: "选择角色", emotion: `${currentCharacter().name} · 表情`, background: "选择背景" }[pickerKind];
    sheet.querySelectorAll("[data-picker-kind]").forEach((button) => {
      const selected = button.dataset.pickerKind === pickerKind;
      button.classList.toggle("active", selected);
      button.setAttribute("aria-selected", String(selected));
    });
    const grid = sheet.querySelector(".picker-grid");
    if (pickerCatalog !== catalog) {
      const focusedKey = document.activeElement?.dataset.assetKey;
      pickerButtons = new Map();
      const fragment = document.createDocumentFragment();
      for (const item of items) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "picker-tile";
        button.dataset.assetKey = item.key;
        button.dataset.custom = item.custom ? "1" : "0";
        button.setAttribute("aria-label", item.custom ? `自定义，${item.label}` : item.label);
        const image = document.createElement("img");
        image.src = item.src;
        image.alt = "";
        image.loading = "lazy";
        const label = document.createElement("span");
        label.textContent = item.label;
        button.append(image, label);
        if (item.custom) {
          const badge = document.createElement("em");
          badge.className = "picker-badge";
          badge.textContent = "自定义";
          button.append(badge);
        }
        button.addEventListener("click", () => {
          if (pickerKind === "character") {
            characterSelect.value = item.key;
            updateEmotionOptions("1");
            saveGeneratorState({ character: item.key, emotion: "1" });
          } else if (pickerKind === "background") {
            backgroundSelect.value = item.key;
            saveGeneratorState({ background: item.key });
          } else {
            emotionSelect.value = item.key;
            saveGeneratorState({ emotion: item.key });
          }
          updateControlLabels();
          requestRender();
          closeOverlay(sheet);
          syncWorkspace();
          if (document.querySelector(".asset-modal.open") && currentAssetTab === "emotion") renderAssetManager({ resetScroll: true });
        });
        pickerButtons.set(item.key, button);
        fragment.appendChild(button);
      }
      grid.replaceChildren(fragment);
      pickerCatalog = catalog;
      if (focusedKey && pickerButtons.has(focusedKey)) pickerButtons.get(focusedKey).focus({ preventScroll: true });
    }
    sheet.querySelectorAll("[data-picker-scope]").forEach((button) => button.setAttribute("aria-pressed", String(button.dataset.pickerScope === pickerScope)));
    let visible = 0;
    for (const item of items) {
      const button = pickerButtons.get(item.key);
      const scopeHidden = pickerScope === "custom" ? button.dataset.custom !== "1" : pickerScope === "builtin" ? button.dataset.custom === "1" : false;
      button.hidden = scopeHidden || Boolean(query && !item.label.toLocaleLowerCase().includes(query));
      const selected = item.key === current;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
      if (!button.hidden) visible++;
    }
    let empty = grid.querySelector(".empty-message");
    if (!empty) { empty = document.createElement("p"); empty.className = "empty-message"; grid.appendChild(empty); }
    empty.textContent = pickerScope === "custom" && !query ? "还没有自定义素材。可在主界面点“自定义素材”添加角色、表情或背景。" : "没有找到素材";
    empty.hidden = visible > 0;
    scheduleAccessibilityReport();
  }

  window.openMaterialPicker = function openMaterialPicker(kind) {
    pickerKind = kind;
    const sheet = ensurePicker();
    sheet.querySelector("[data-picker-search]").value = "";
    renderPicker();
    openOverlay(sheet, "picker");
    requestAnimationFrame(() => pickerButtons.get(kind === "character" ? characterSelect.value : kind === "background" ? backgroundSelect.value : emotionSelect.value)?.scrollIntoView({ block: "nearest" }));
  };

  function ensureSettings() {
    let sheet = document.querySelector(".generator-settings");
    if (sheet) return sheet;
    sheet = document.createElement("section");
    sheet.className = "generator-settings";
    sheet.setAttribute("aria-label", "设置");
    sheet.innerHTML = `
      <div class="asset-header"><strong>设置</strong><button type="button" data-settings-close class="icon-button" title="关闭" aria-label="关闭设置"><i data-lucide="x"></i></button></div>
      <div class="settings-content">
        <div class="settings-field"><label>界面配色</label><div class="segmented" role="group" aria-label="界面配色">${UI_THEME_PRESETS.map((item) => `<button type="button" data-theme-key="${item.key}">${item.label}</button>`).join("")}</div></div>
        <div class="settings-field"><label for="exportSizeSetting">QQ 图片尺寸</label><select id="exportSizeSetting">${QQ_EXPORT_PRESETS.map((item) => `<option value="${item.key}">${item.label} · ${item.width} px</option>`).join("")}</select></div>
        <div class="settings-field"><label for="textKeyboardSetting">文字编辑键盘</label><select id="textKeyboardSetting"><option value="system">使用手机原键盘</option><option value="builtin">使用魔裁内置键盘</option></select><small class="settings-help">使用手机原键盘时，完成编辑后系统可能要求选回魔裁；内置键盘可留在当前输入法流程。语音由手机识别服务处理，部分手机可能需要联网；麦克风权限只会在点语音按钮时请求。</small></div>
        <div class="settings-field" data-opacity-field><label for="popupOpacitySetting">内容透明度</label><div class="settings-range"><input id="popupOpacitySetting" type="range" min="70" max="100" step="1" aria-label="内容透明度" /><output id="popupOpacityValue">100%</output></div></div>
        <button type="button" class="settings-link" data-settings-assets><i data-lucide="library"></i>素材管理</button>
        <button type="button" class="settings-link" data-settings-about><i data-lucide="info"></i>关于与隐私</button>
      </div>`;
    document.body.appendChild(sheet);
    sheet.querySelector("[data-settings-close]").addEventListener("click", () => closeOverlay(sheet));
    sheet.querySelectorAll("[data-theme-key]").forEach((button) => button.addEventListener("click", () => {
      saveUiThemeKey(button.dataset.themeKey); applyUiTheme(button.dataset.themeKey); updateSettings();
    }));
    sheet.querySelector("#exportSizeSetting").addEventListener("change", (event) => saveQqExportSizeKey(event.target.value));
    sheet.querySelector("#textKeyboardSetting").addEventListener("change", (event) => saveTextKeyboardMode(event.target.value));
    sheet.querySelector("#popupOpacitySetting").addEventListener("input", (event) => {
      const value = Number(event.target.value);
      sheet.querySelector("#popupOpacityValue").textContent = `${value}%`;
      window.AndroidBridge?.setPopupOpacity?.(value);
    });
    sheet.querySelector("[data-settings-assets]").addEventListener("click", openAssetManager);
    sheet.querySelector("[data-settings-about]").addEventListener("click", openAboutModal);
    icons();
    return sheet;
  }

  function updateSettings() {
    const sheet = ensureSettings();
    sheet.querySelectorAll("[data-theme-key]").forEach((button) => button.setAttribute("aria-pressed", String(button.dataset.themeKey === loadUiThemeKey())));
    sheet.querySelector("#exportSizeSetting").value = loadQqExportSizeKey();
    sheet.querySelector("#textKeyboardSetting").value = loadTextKeyboardMode();
    const hasOpacity = typeof window.AndroidBridge?.setPopupOpacity === "function";
    sheet.querySelector("[data-opacity-field]").hidden = !hasOpacity;
    const value = hasOpacity && typeof window.AndroidBridge.getPopupOpacity === "function" ? Number(window.AndroidBridge.getPopupOpacity()) : 100;
    sheet.querySelector("#popupOpacitySetting").value = String(value);
    sheet.querySelector("#popupOpacityValue").textContent = `${value}%`;
  }

  window.openGeneratorSettings = function openGeneratorSettings() {
    updateSettings(); openOverlay(ensureSettings(), "settings");
  };
  window.refreshInputMethodStatus = function refreshInputMethodStatus() {
    let status = {};
    try { status = JSON.parse(window.AndroidBridge?.getInputMethodStatus?.() || "{}"); } catch (_) {}
    const imeButton = document.getElementById("modeImeButton");
    const popupButton = document.getElementById("modePopupButton");
    if (imeButton) imeButton.setAttribute("aria-pressed", String(status.mode !== "popup" && Boolean(status.selected)));
    if (popupButton) popupButton.setAttribute("aria-pressed", String(status.mode === "popup"));
    const sheet = document.querySelector(".chat-modes.open");
    if (!sheet) return;
    sheet.querySelector("[data-ime-status]").textContent = status.mode === "popup" ? "当前：悬浮窗模式" : status.selected ? "当前：魔裁图片输入法" : status.enabled ? "图片输入法已启用，还没选中" : "图片输入法尚未启用";
    sheet.querySelector("[data-select-ime]").setAttribute("aria-pressed", String(status.mode !== "popup"));
    sheet.querySelector("[data-open-popup]").setAttribute("aria-pressed", String(status.mode === "popup"));
    sheet.querySelector("[data-enable-ime]").hidden = Boolean(status.enabled);
    sheet.querySelector("[data-select-ime]").disabled = !status.enabled;
  };
  window.openChatModes = function openChatModes() {
    let sheet = document.querySelector(".chat-modes");
    if (!sheet) {
      sheet = document.createElement("section");
      sheet.className = "generator-settings chat-modes";
      sheet.setAttribute("aria-label", "聊天模式");
      sheet.innerHTML = `<div class="asset-header"><strong>聊天模式</strong><button type="button" data-close-modes class="icon-button" aria-label="关闭聊天模式" title="关闭"><i data-lucide="x"></i></button></div><div class="settings-content"><output data-ime-status role="status"></output><div class="segmented chat-mode-options" role="group" aria-label="聊天模式"><button type="button" data-select-ime><i data-lucide="keyboard"></i>图片输入法</button><button type="button" data-open-popup><i data-lucide="panels-top-left"></i>悬浮窗</button></div><button type="button" data-enable-ime class="primary"><i data-lucide="keyboard"></i>启用图片输入法</button></div>`;
      document.body.appendChild(sheet);
      sheet.querySelector("[data-close-modes]").addEventListener("click", () => closeOverlay(sheet));
      sheet.querySelector("[data-enable-ime]").addEventListener("click", () => window.AndroidBridge?.openInputMethodSettings?.());
      sheet.querySelector("[data-select-ime]").addEventListener("click", () => window.AndroidBridge?.showInputMethodPicker?.());
      sheet.querySelector("[data-open-popup]").addEventListener("click", async () => {
        let status = {};
        try { status = JSON.parse(window.AndroidBridge?.getInputMethodStatus?.() || "{}"); } catch (_) {}
        if (status.mode !== "popup" && !await showConfirmDialog({ title: "改用悬浮窗？", message: "将关闭输入法模式的快捷入口，两种聊天模式不会同时运行。", confirmText: "切换", danger: false })) return;
        closeOverlay(sheet);
        window.AndroidBridge?.startPopupMode?.();
      });
      icons();
    }
    openOverlay(sheet, "modes");
    window.refreshInputMethodStatus();
  };
  document.getElementById("modeImeButton")?.addEventListener("click", () => {
    let status = {};
    try { status = JSON.parse(window.AndroidBridge?.getInputMethodStatus?.() || "{}"); } catch (_) {}
    if (!status.enabled) { window.AndroidBridge?.openInputMethodSettings?.(); return; }
    if (status.mode !== "popup" && status.selected) return;
    window.AndroidBridge?.useImageMode?.();
  });
  document.getElementById("modePopupButton")?.addEventListener("click", async () => {
    let status = {};
    try { status = JSON.parse(window.AndroidBridge?.getInputMethodStatus?.() || "{}"); } catch (_) {}
    if (status.mode === "popup") return;
    if (!await showConfirmDialog({ title: "改用悬浮窗？", message: "图片面板将收起，文字键盘仍可使用。", confirmText: "切换", danger: false })) return;
    window.AndroidBridge?.startPopupMode?.();
  });
  document.getElementById("openCustomButton")?.addEventListener("click", () => { if (typeof openAssetManager === "function") openAssetManager(); });
  document.getElementById("pickCharacter")?.addEventListener("click", () => window.openMaterialPicker("character"));
  document.getElementById("pickEmotion")?.addEventListener("click", () => window.openMaterialPicker("emotion"));
  document.getElementById("pickBackground")?.addEventListener("click", () => window.openMaterialPicker("background"));
  document.getElementById("dockSettingsButton")?.addEventListener("click", window.openGeneratorSettings);
  document.getElementById("clearTextButton")?.addEventListener("click", () => {
    lineText.value = ""; saveGeneratorState({ text: "" }); requestRender(); window.beginInlineTextEditing();
  });
  window.addEventListener("generatorchange", syncWorkspace);
  window.addEventListener("assetschange", () => { pickerCatalog = ""; syncWorkspace(); });
  window.addEventListener("storage", (event) => {
    if (event.key === STORAGE_KEYS.uiTheme) applyUiTheme(loadUiThemeKey());
  });
  window.addEventListener("focus", () => { applyGeneratorState(window.MahouState.current()); applyUiTheme(loadUiThemeKey()); window.refreshInputMethodStatus(); });
  syncWorkspace();
  window.refreshInputMethodStatus();
  icons();
})();
