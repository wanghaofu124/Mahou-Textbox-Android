(function () {
  "use strict";
  let target = {};
  let sendState = "idle";
  let sendStateTimer = 0;
  const qqButton = document.getElementById("qqButton");
  const qqButtonLabel = qqButton.querySelector("span");
  const idleSendLabel = "放入 QQ";

  function resetSendButton() {
    sendState = "idle";
    qqButton.dataset.sendState = "idle";
    qqButtonLabel.textContent = idleSendLabel;
    qqButton.title = "";
    qqButton.setAttribute("aria-label", "把图片放进当前 QQ 输入框");
    qqButton.disabled = !target.canSend;
  }

  window.receiveInputTarget = function (value) {
    try {
      const info = typeof value === "string" ? JSON.parse(value) : value;
      target = info;
      clearTimeout(sendStateTimer);
      if (sendState !== "busy") resetSendButton();
      document.getElementById("readDraftButton").disabled = info.sensitive || !info.active;
      document.getElementById("inputTargetStatus").textContent = info.sensitive ? "安全输入" : info.canSend ? "当前聊天" : "图片台词";
    } catch (_) {}
  };
  window.onInputEnded = () => {
    target.active = false;
    target.canSend = false;
    document.getElementById("inputTargetStatus").textContent = "图片台词";
    if (sendState !== "busy") resetSendButton();
    document.getElementById("readDraftButton").disabled = true;
  };
  window.receiveImeSendState = function (state, message) {
    clearTimeout(sendStateTimer);
    sendState = state;
    const labels = {
      busy: "处理中…",
      inserted: "已放入",
      ready: "点+发送",
      error: "重试",
    };
    qqButton.dataset.sendState = state;
    qqButtonLabel.textContent = labels[state] || idleSendLabel;
    qqButton.title = message || "";
    qqButton.setAttribute("aria-label", message || "把图片放进当前 QQ 输入框");
    qqButton.disabled = state === "busy" || !target.canSend;
    if (state !== "busy" && state !== "idle") {
      sendStateTimer = setTimeout(() => {
        if (sendState === state) resetSendButton();
      }, state === "ready" ? 6500 : 4000);
    }
  };
  document.getElementById("readDraftButton").addEventListener("click", () => window.AndroidBridge?.importCurrentDraft?.());
  document.getElementById("hideImeButton").addEventListener("click", () => window.AndroidBridge?.hidePanel?.());
  document.getElementById("previousKeyboardButton").addEventListener("click", () => window.AndroidBridge?.previousKeyboard?.());
  document.getElementById("imeEditorButton").addEventListener("click", () => window.AndroidBridge?.openMainEditor?.());
  window.setupImageDoubleTap(document.getElementById("preview"), () => window.AndroidBridge?.openCaptionEditor?.());
  window.AndroidBridge?.requestInputTarget?.();
})();
