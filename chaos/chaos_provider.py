"""A fake model provider for showing timeouts and retries: OpenAI-compatible, and it answers as the question says.

LiteLLM calls it under the alias chat-chaos (litellm/config.yaml), like any provider, so its timeout and retries apply,
and so do the app's. It calls nothing further. The last user message chooses what happens:

    slow 20         every attempt answers after 20 seconds
    429             every attempt answers 429, a rate limit
    500             every attempt answers 500
    1x slow 20      the first attempt waits 20 seconds, the next answers at once
    2x slow 20      the first 2 attempts wait 20 seconds, the third answers at once; also 1x 500, 2x 429 and so on

Anything else is answered at once with the question, and the first such answer of a conversation adds how to make the
provider misbehave, for someone who chose it without knowing. An answer reports usage as a real model would, the whole
prompt and the answer counted in tokens, so the gateway's spend logs show what each attempt sent. Every request it
receives is logged with the attempt number of its question, so `docker compose logs -f chaos-provider` shows how many
times one question reached the provider. A question counts from 1 again when it comes more than a few seconds after its
answer, or after a pause longer than a turn (70s in application.yml): a retry follows within a second or two, a person
asking again takes longer.
"""

import json
import select
import socket
import re
import sys
import threading
import time
import uuid
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# "2x slow 20", "500": how many attempts misbehave, every one without a count, and how.
CHAOS = re.compile(r"(?:\b(\d+)\s*x\s*)?\b(slow (\d+)|429|500)\b", re.IGNORECASE)
QUIET = 75
ASKED_AGAIN = 5
CONTEXT = re.compile(r"^\s*<context>.*?</context>", re.DOTALL)
HELP = (
    "I am a fake provider: I call no model and answer as the question says. Ask 429 or 500 to fail every attempt, "
    "slow 20 to answer after 20 seconds, or 1x 500 to fail only the first attempt."
)
# What OpenAI's tokenizer adds around each message and before the reply.
MESSAGE_TOKENS = 3
REPLY_TOKENS = 3

attempts: dict[str, tuple[int, float]] = {}
answered: dict[str, float] = {}
lock = threading.Lock()


def log(message: str) -> None:
    print(f"{datetime.now():%H:%M:%S.%f}"[:-3] + f"  {message}", flush=True)


def content(message: dict) -> str:
    text = message.get("content") or ""
    if isinstance(text, list):
        text = " ".join(part.get("text", "") for part in text if isinstance(part, dict))
    return text


def tokens(text: str) -> int:
    """Roughly what a real model's tokenizer counts: about four characters of English per token."""
    return max(1, round(len(text) / 4)) if text else 0


def first_question(body: dict) -> bool:
    """Whether the conversation holds no answer yet: the chat sends its earlier answers with every question."""
    return not any(message.get("role") == "assistant" for message in body.get("messages", []))


def question(body: dict) -> str:
    for message in reversed(body.get("messages", [])):
        if message.get("role") == "user":
            # The chat puts its context block before the question (chat ADR 0008); the question is what follows it.
            return CONTEXT.sub("", content(message)).strip()
    return ""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self) -> None:  # noqa: N802
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        text = question(body)
        key = text.lower()
        now = time.monotonic()
        with lock:
            count, last = attempts.get(key, (0, now))
            again = now - answered.pop(key, now) > ASKED_AGAIN
            attempt = 1 if again or now - last > QUIET else count + 1
            attempts[key] = (attempt, now)
        log(f"attempt {attempt}  {text[:60]!r}")

        chaos = CHAOS.search(text)
        times = None if chaos is None else int(chaos.group(1)) if chaos.group(1) else attempt
        if chaos and attempt <= times:
            what = chaos.group(2).lower()
            if what == "429":
                return self.error(429, "chaos: rate limited")
            if what == "500":
                return self.error(500, "chaos: 500")
            seconds = int(chaos.group(3))
            log(f"  waits {seconds}s")
            time.sleep(seconds)
            if self.caller_gone():
                return log("  caller gave up before the answer")
        # An answer at the very moment the caller's timeout runs out may still be dropped and retried, so the count
        # starts again only for a request that comes later than a retry would.
        reply = f"chaos answered: {text}"
        if chaos is None and first_question(body):
            reply += "\n\n" + HELP
        if self.answer(body, reply):
            with lock:
                answered[key] = time.monotonic()

    def caller_gone(self) -> bool:
        """Whether the caller closed the connection while we waited, as LiteLLM does when its timeout runs out. A
        write to such a connection may still succeed into the socket's buffer, so it would count as answered."""
        readable, _, _ = select.select([self.connection], [], [], 0)
        try:
            return bool(readable) and self.connection.recv(1, socket.MSG_PEEK) == b""
        except OSError:
            return True

    def error(self, status: int, message: str) -> None:
        log(f"  -> {status}")
        payload = json.dumps({"error": {"message": message, "type": "chaos", "code": status}}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def answer(self, body: dict, text: str) -> bool:
        prompt = sum(MESSAGE_TOKENS + tokens(content(message)) for message in body.get("messages", [])) + REPLY_TOKENS
        completion = tokens(text)
        usage = {"prompt_tokens": prompt, "completion_tokens": completion, "total_tokens": prompt + completion}
        # A fresh id per answer: LiteLLM keys its spend log on it and keeps only the first answer of an id.
        base = {"id": f"chaos-{uuid.uuid4()}", "created": int(time.time()), "model": "chaos"}
        try:
            if body.get("stream"):
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Connection", "close")
                self.end_headers()
                chunks = [
                    {**base, "object": "chat.completion.chunk",
                     "choices": [{"index": 0, "delta": {"role": "assistant", "content": text}, "finish_reason": None}]},
                    {**base, "object": "chat.completion.chunk",
                     "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]},
                    {**base, "object": "chat.completion.chunk", "choices": [], "usage": usage},
                ]
                for chunk in chunks:
                    self.wfile.write(f"data: {json.dumps(chunk)}\n\n".encode())
                self.wfile.write(b"data: [DONE]\n\n")
                self.close_connection = True
            else:
                payload = json.dumps({**base, "object": "chat.completion", "usage": usage, "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": text}, "finish_reason": "stop"}]}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            log("  -> 200")
            return True
        except (BrokenPipeError, ConnectionResetError):
            log("  caller gave up before the answer")
            return False

    def log_message(self, format: str, *args) -> None:  # noqa: A002
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    log(f"chaos provider on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
