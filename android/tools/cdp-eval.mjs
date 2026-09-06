#!/usr/bin/env node

const endpoint = process.env.YTMNT_CDP_ENDPOINT ?? "http://127.0.0.1:9223";
const expression = process.argv.slice(2).join(" ");

if (!expression) {
  console.error("Usage: node tools/cdp-eval.mjs '<JavaScript expression>'");
  process.exit(2);
}

const targets = await fetch(`${endpoint}/json/list`).then((response) => response.json());
const target = targets.find((candidate) => candidate.url?.startsWith("https://music.youtube.com"));

if (!target) {
  throw new Error("No debuggable YouTube Music WebView target found");
}

const result = await new Promise((resolve, reject) => {
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  const timer = setTimeout(() => {
    socket.close();
    reject(new Error("Timed out waiting for the WebView"));
  }, 10_000);

  socket.addEventListener("open", () => {
    socket.send(JSON.stringify({
      id: 1,
      method: "Runtime.evaluate",
      params: {
        expression,
        awaitPromise: true,
        returnByValue: true,
      },
    }));
  });

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.id !== 1) return;
    clearTimeout(timer);
    socket.close();
    if (message.error) reject(new Error(message.error.message));
    else resolve(message.result);
  });

  socket.addEventListener("error", () => {
    clearTimeout(timer);
    reject(new Error("Could not connect to the WebView debug socket"));
  });
});

console.log(JSON.stringify(result, null, 2));

