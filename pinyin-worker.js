import { requestCandidates } from "./vendor/simple-ime/src/engine/index.ts";

self.onmessage = ({ data }) => {
  const { id, text } = data;
  if (typeof text !== "string" || !/^[a-z']{1,24}$/.test(text)) {
    self.postMessage({ id, candidates: [] });
    return;
  }
  try {
    const result = requestCandidates(text);
    self.postMessage({ id, candidates: result.candidates.slice(0, 80) });
  } catch (_) {
    self.postMessage({ id, candidates: [] });
  }
};
