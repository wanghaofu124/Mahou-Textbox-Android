(function () {
  "use strict";
  const textArea = document.getElementById("lineText");
  let initialized = false;
  let keyboard = null;

  document.getElementById("finishImeEditor").addEventListener("click", () => {
    finishInput();
    window.AndroidBridge?.finishImeEditing?.();
  });
  const sendButton = document.getElementById("sendImeEditor");
  sendButton.addEventListener("click", () => {
    finishInput();
    const accepted = window.AndroidBridge?.sendImeEditingToQQ?.();
    if (accepted) sendButton.disabled = true;
    else showAppNotice("请从 QQ 或 TIM 聊天中使用“发到 QQ”");
  });
  function leaveEditor() {
    finishInput();
    window.AndroidBridge?.closeImeEditing?.();
  }
  document.getElementById("leaveImeEditor").addEventListener("click", leaveEditor);
  document.getElementById("editorScrim")?.addEventListener("click", leaveEditor);

  function insertText(value) {
    const text = String(value || "");
    const start = textArea.selectionStart ?? textArea.value.length;
    const end = textArea.selectionEnd ?? start;
    const room = Math.max(0, 180 - (textArea.value.length - (end - start)));
    const accepted = text.slice(0, room);
    textArea.setRangeText(accepted, start, end, "end");
    textArea.dispatchEvent(new Event("input", { bubbles: true }));
    if (accepted.length < text.length) window.showAppNotice?.("台词最多可输入 180 个字符");
    return accepted.length === text.length;
  }

  function editText(action) {
    let start = textArea.selectionStart ?? textArea.value.length;
    let end = textArea.selectionEnd ?? start;
    if (action === "enter") return insertText("\n");
    if (action === "all") {
      textArea.setSelectionRange(0, textArea.value.length);
      return;
    }
    if (action === "left" || action === "right") {
      const caret = start !== end
        ? (action === "left" ? start : end)
        : Math.max(0, Math.min(textArea.value.length, start + (action === "left" ? -1 : 1)));
      textArea.setSelectionRange(caret, caret);
      return;
    }
    if (action === "delete") {
      if (start === end && start > 0) {
        const previous = textArea.value.charCodeAt(start - 1);
        if (previous >= 0xdc00 && previous <= 0xdfff && start > 1) start--;
        start--;
      }
      textArea.setRangeText("", start, end, "end");
      textArea.dispatchEvent(new Event("input", { bubbles: true }));
    }
  }

  window.insertRecognizedText = insertText;
  window.setImeVoicePreview = (text, listening, preparing) => keyboard?.setVoicePreview(text, listening, preparing);
  window.initializeImeEditor = function initializeImeEditor() {
    if (initialized) return;
    initialized = true;
    if (!window.AndroidBridge?.useBuiltInTextKeyboard?.()) {
      window.beginInlineTextEditing();
      return;
    }
    document.documentElement.classList.add("builtin-keyboard");
    textArea.readOnly = true;
    textArea.inert = true;
    document.getElementById("builtinKeyboard").hidden = false;
    textArea.setSelectionRange(textArea.value.length, textArea.value.length);
    keyboard = new window.MahouTouchKeyboard(document.getElementById("builtinKeyboard"), {
      commit: insertText,
      preedit: (text) => { window.imePreedit = text; requestRender(); },
      edit: editText,
      voice: () => {
        keyboard.setVoicePreview("正在准备本机语音包…", false, true);
        window.showAppNotice?.("正在准备语音输入…", 10000);
        window.AndroidBridge?.startVoiceInput?.();
      },
      notice: (message) => window.showAppNotice?.(message),
    });
  };

  requestAnimationFrame(() => window.initializeImeEditor());
})();
