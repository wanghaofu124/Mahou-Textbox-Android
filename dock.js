window.setNativePopupState = function setNativePopupState(value) {
  try {
    const state = typeof value === "string" ? JSON.parse(value) : value;
    document.documentElement.classList.toggle("ime-visible", Boolean(state.imeVisible));
    document.documentElement.dataset.windowMode = state.mode || "full";
  } catch (_) {}
};
