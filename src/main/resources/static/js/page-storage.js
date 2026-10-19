(function () {
  function storage() {
    try {
      return window.localStorage || null;
    } catch (_) {
      return null;
    }
  }

  function read(key) {
    var store = storage();
    if (!store) return null;
    try {
      return store.getItem(key);
    } catch (_) {
      return null;
    }
  }

  function write(key, value) {
    var store = storage();
    if (!store) return false;
    try {
      store.setItem(key, value);
      return true;
    } catch (_) {
      return false;
    }
  }

  function readJson(key, fallback) {
    var raw = read(key);
    if (raw === null || raw === undefined || raw === '') return fallback;
    try {
      return JSON.parse(raw);
    } catch (_) {
      return fallback;
    }
  }

  function writeJson(key, value) {
    try {
      return write(key, JSON.stringify(value));
    } catch (_) {
      return false;
    }
  }

  function remove(key) {
    var store = storage();
    if (!store) return;
    try {
      store.removeItem(key);
    } catch (_) {}
  }

  window.PageStorage = {
    read: read,
    write: write,
    readJson: readJson,
    writeJson: writeJson,
    remove: remove,
  };
})();
