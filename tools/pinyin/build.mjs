import { build } from "esbuild";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const here = path.dirname(fileURLToPath(import.meta.url));
await build({
  entryPoints: [path.join(here, "../../pinyin-worker.js")],
  outfile: path.join(here, "../../pinyin/engine.js"),
  bundle: true, minify: true, target: "chrome80", format: "iife",
  nodePaths: [path.join(here, "node_modules")],
  drop: ["console"], legalComments: "eof",
  plugins: [{ name: "local-dictionary", setup(builder) {
    builder.onResolve({ filter: /\?raw$/ }, args => ({
      path: path.resolve(args.resolveDir, args.path.slice(0, -4)), namespace: "dictionary"
    }));
    builder.onLoad({ filter: /.*/, namespace: "dictionary" }, async args => ({
      contents: await readFile(args.path, "utf8"), loader: "text"
    }));
  } }]
});
