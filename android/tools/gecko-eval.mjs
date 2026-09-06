import net from "node:net";

const port = Number(process.env.GECKO_DEBUG_PORT || 9224);
const expression = process.argv.slice(2).join(" ") || "location.href";
const socket = net.connect(port, "127.0.0.1");

let buffer = Buffer.alloc(0);
let stage = "greeting";
let consoleActor;

function send(packet) {
  const json = JSON.stringify(packet);
  socket.write(`${Buffer.byteLength(json)}:${json}`);
}

function finish(value) {
  process.stdout.write(`${JSON.stringify(value, null, 2)}\n`);
  socket.end();
}

function handle(packet) {
  if (process.env.GECKO_DEBUG_TRACE) {
    process.stderr.write(`${JSON.stringify(packet)}\n`);
  }
  if (stage === "greeting") {
    stage = "tabs";
    send({ to: "root", type: "listTabs" });
    return;
  }

  if (stage === "tabs" && packet.tabs?.[0]) {
    stage = "target";
    send({ to: packet.tabs[0].actor, type: "getTarget" });
    return;
  }

  if (stage === "target" && packet.frame?.consoleActor) {
    consoleActor = packet.frame.consoleActor;
    stage = "evaluation";
    send({ to: consoleActor, type: "evaluateJSAsync", text: expression });
    return;
  }

  if (stage === "evaluation" && packet.type === "evaluationResult") {
    const value = packet.result?.value ?? packet.result ?? null;
    finish({
      exception: packet.exceptionMessage || null,
      result: value,
    });
  }
}

socket.on("data", (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  while (true) {
    const colon = buffer.indexOf(58);
    if (colon < 0) return;
    const length = Number(buffer.subarray(0, colon).toString());
    if (!Number.isFinite(length) || buffer.length < colon + 1 + length) return;
    const start = colon + 1;
    const packet = JSON.parse(buffer.subarray(start, start + length).toString());
    buffer = buffer.subarray(start + length);
    handle(packet);
  }
});

socket.on("error", (error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});

setTimeout(() => {
  process.stderr.write("Timed out waiting for Gecko debugger\n");
  socket.destroy();
  process.exitCode = 1;
}, 8_000).unref();
