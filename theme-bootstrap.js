(function () {
  try {
    var key = localStorage.getItem("mahou.uiTheme");
    var allowed = ["dark", "night", "light", "mono"];
    if (key && allowed.indexOf(key) !== -1) {
      document.documentElement.dataset.theme = key;
      var colors = { dark: "#17181d", night: "#000000", light: "#f4f4f6", mono: "#0a0a0a" };
      var meta = document.querySelector('meta[name="theme-color"]');
      if (meta && colors[key]) meta.content = colors[key];
    }
  } catch (error) {
    // The regular theme initialization will apply the default.
  }
})();
