(function () {
  "use strict";
  class TouchKeyboard {
    constructor(root, options) {
      this.root = root;
      this.options = options;
      this.composition = "";
      this.voicePreview = "";
      this.voiceListening = false;
      this.candidates = [];
      this.sequence = 0;
      this.workerReady = false;
      this.queryTimer = 0;
      this.chinese = true;
      this.symbols = false;
      this.upper = false;
      this.sensitive = false;
      root.innerHTML = '<div class="key-composition"><span class="key-composition-label">预输入</span><output aria-label="拼音或语音预输入" data-placeholder="拼音或语音识别内容会显示在这里"></output><button type="button" data-action="cancel" aria-label="清除拼音" title="清除拼音"><i data-lucide="x"></i></button></div><div class="key-candidates" role="group" aria-label="候选词"></div><div class="key-rows"></div>';
      this.output = root.querySelector("output");
      this.compositionLabel = root.querySelector(".key-composition-label");
      this.candidateRow = root.querySelector(".key-candidates");
      root.querySelector('[data-action="cancel"]').addEventListener("click", () => this.clear());
      this.startWorker();
      this.renderKeys();
      this.renderCandidates();
    }
    startWorker() {
      if (this.worker) this.worker.terminate();
      try {
        this.worker = new Worker("pinyin/engine.js");
        this.worker.onmessage = ({ data }) => {
          if (data.id === 0) {
            this.workerReady = true;
            if (this.composition) this.query();
            return;
          }
          if (data.id !== this.sequence) return;
          clearTimeout(this.timeout);
          this.candidates = data.candidates;
          this.renderCandidates();
        };
        this.worker.onerror = () => {
          this.workerReady = false;
          clearTimeout(this.timeout);
          clearTimeout(this.queryTimer);
          this.candidates = [];
          this.renderCandidates();
        };
        // A short warm-up response tells us when the bundled dictionary has loaded.
        // Queries typed during startup are held until then instead of killing the worker.
        this.worker.postMessage({ id: 0, text: "a" });
      } catch (_) { this.worker = null; this.workerReady = false; }
    }
    clear() {
      ++this.sequence;
      clearTimeout(this.timeout);
      clearTimeout(this.queryTimer);
      this.composition = "";
      this.candidates = [];
      this.renderCandidates();
    }
    setVoicePreview(text, listening = false) {
      this.voicePreview = String(text || "");
      this.voiceListening = Boolean(listening);
      this.renderCandidates();
    }
    configure({ sensitive = false, numeric = false } = {}) {
      this.clear();
      this.sensitive = sensitive;
      this.symbols = numeric;
      if (sensitive) this.chinese = false;
      this.renderKeys();
    }
    query() {
      this.candidates = [];
      this.renderCandidates();
      const id = ++this.sequence;
      clearTimeout(this.timeout);
      clearTimeout(this.queryTimer);
      if (!this.composition) return;
      if (!this.worker || !this.workerReady) return;
      const text = this.composition;
      this.queryTimer = setTimeout(() => {
        if (id !== this.sequence || !this.workerReady) return;
        this.worker.postMessage({ id, text });
        this.timeout = setTimeout(() => {
          if (id === this.sequence) this.options.notice?.("候选词还在加载，点拼音可直接输入");
        }, 4000);
      }, 35);
    }
    choose(candidate) {
      if (!this.composition) return;
      const text = candidate ? candidate.w : this.composition;
      if (this.options.commit(text) === false) return;
      this.composition = candidate ? this.composition.slice(candidate.matchLength) : "";
      this.query();
    }
    flush() { if (this.composition) this.choose(this.candidates[0]); }
    press(key) {
      if (key === "delete") {
        if (this.composition) { this.composition = this.composition.slice(0, -1); this.query(); }
        else this.options.edit("delete");
      } else if (key === "voice") {
        this.flush();
        this.options.voice?.();
      } else if (key === "language") {
        this.flush(); this.chinese = !this.chinese; this.symbols = false; this.renderKeys(); this.renderCandidates();
      } else if (key === "symbols") {
        this.flush(); this.symbols = !this.symbols; this.renderKeys();
      } else if (key === "shift") {
        this.upper = !this.upper; this.renderKeys();
      } else if (key === "space") {
        if (this.composition) this.choose(this.candidates[0]); else this.options.commit(" ");
      } else if (key === "enter") {
        if (this.composition) this.choose(this.candidates[0]); else this.options.edit("enter");
      } else if (["left", "right", "all"].includes(key)) {
        this.flush(); this.options.edit(key);
      } else if (this.chinese && !this.symbols && !this.sensitive && /^[a-z']$/.test(key)) {
        if (this.composition.length >= 24) { this.options.notice?.("请先选择候选词"); return; }
        this.composition += key;
        this.query();
      } else {
        this.flush();
        this.options.commit(key);
      }
    }
    button(label, action, icon, className = "") {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `touch-key ${className}`;
      button.setAttribute("aria-label", label);
      button.title = label;
      if (icon) button.innerHTML = `<i data-lucide="${icon}"></i>`;
      else button.textContent = label;
      let timer, repeated = false;
      const stop = () => { clearTimeout(timer); clearInterval(timer); };
      button.addEventListener("pointerdown", event => {
        repeated = false;
        if (action === "delete") {
          button.setPointerCapture(event.pointerId);
          timer = setTimeout(() => {
            repeated = true;
            this.press(action);
            timer = setInterval(() => this.press(action), 65);
          }, 400);
        }
      });
      button.addEventListener("pointerup", stop);
      button.addEventListener("pointercancel", () => { stop(); repeated = true; });
      button.addEventListener("lostpointercapture", stop);
      button.addEventListener("click", () => { if (!repeated) this.press(action); repeated = false; });
      return button;
    }
    renderKeys() {
      const area = this.root.querySelector(".key-rows");
      const letters = this.upper && !this.chinese ? ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"] : ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
      const rows = this.symbols ? ["1234567890", "@#%&*()-+", "，。？！、：；"] : letters;
      const fragment = document.createDocumentFragment();
      rows.forEach((chars, index) => {
        const row = document.createElement("div"); row.className = `key-row row-${index}`;
        if (index === 2) row.appendChild(this.button(this.symbols ? "字母" : "大写", this.symbols ? "symbols" : "shift", this.symbols ? null : "arrow-up", "wide-key"));
        for (const char of chars) row.appendChild(this.button(char, char));
        if (index === 2) row.appendChild(this.button("删除", "delete", "delete", "wide-key"));
        fragment.appendChild(row);
      });
      const row = document.createElement("div"); row.className = "key-row bottom-row";
      row.append(this.button(this.symbols ? "ABC" : "123", "symbols", null, "wide-key"),
        this.button(this.chinese && !this.sensitive ? "中" : "EN", "language"),
        this.button(this.chinese ? "，" : ",", this.chinese ? "，" : ","),
        this.button("空格", "space", null, "space-key"),
        this.button(this.chinese ? "。" : ".", this.chinese ? "。" : "."),
        this.button(this.options.enterLabel?.() || "换行", "enter", "corner-down-left", "wide-key"));
      fragment.appendChild(row); area.replaceChildren(fragment);
      window.lucide?.createIcons({ attrs: { "aria-hidden": "true" } });
    }
    renderCandidates() {
      const preview = this.composition || this.voicePreview;
      this.output.textContent = preview;
      this.output.dataset.placeholder = this.voiceListening && !preview ? "正在听，请说出台词…" : "拼音或语音识别内容会显示在这里";
      this.compositionLabel.textContent = this.composition ? "拼音" : (this.voicePreview || this.voiceListening ? "语音" : "预输入");
      this.root.classList.toggle("composing", Boolean(this.composition));
      this.root.classList.toggle("voice-preview", Boolean(this.voicePreview || this.voiceListening));
      this.root.querySelector('[data-action="cancel"]').hidden = !this.composition;
      const fragment = document.createDocumentFragment();
      if (this.composition) {
        for (const item of this.candidates) {
          const button = document.createElement("button"); button.type = "button";
          button.textContent = item.w; button.setAttribute("aria-label", `候选词 ${item.w}`);
          button.addEventListener("click", () => this.choose(item)); fragment.appendChild(button);
        }
        const raw = document.createElement("button"); raw.type = "button"; raw.textContent = this.composition;
        raw.addEventListener("click", () => this.choose(null)); fragment.appendChild(raw);
      } else {
        fragment.append(this.button("向左移动光标", "left", "chevron-left"), this.button("全选", "all", "text-select"), this.button("向右移动光标", "right", "chevron-right"));
        if (this.options.voice) fragment.append(this.button(
          this.voiceListening ? "结束语音输入" : "语音输入",
          "voice",
          this.voiceListening ? "mic-off" : "mic",
          this.voiceListening ? "voice-active" : ""
        ));
      }
      this.candidateRow.replaceChildren(fragment); this.candidateRow.scrollLeft = 0;
      window.lucide?.createIcons({ attrs: { "aria-hidden": "true" } });
    }
  }
  window.MahouTouchKeyboard = TouchKeyboard;
})();
