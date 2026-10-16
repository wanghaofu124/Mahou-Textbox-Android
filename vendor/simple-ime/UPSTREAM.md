# Offline Pinyin Engine

Source: https://github.com/nieyuyao/simple-ime
Pinned revision: d503bee97392f3b99e1fb9d6f23cecc86ced6f26 (1.2.4)
License: MIT, preserved in LICENSE.

Only the dictionary and candidate engine are included. The upstream browser UI
and global keyboard listeners are not used. Mahou supplies its own touch keyboard.
The build is reproducible with `npm ci && npm run build` in tools/pinyin.
The generated pinyin/engine.js contains its dictionary and never accesses a network.

Dependencies: dawg-lookup 2.2.1 (MIT), lru-cache 11.1.0 (ISC).
Their license files are bundled in pinyin/ alongside the engine.
