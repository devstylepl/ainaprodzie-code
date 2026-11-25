"""What the chaos provider answers when nothing in the question tells it to misbehave (chaos_provider.py): the question
back, and under the first one of a conversation how to make it fail. Run by ChaosProviderTests in the image compose.yaml
gives the chaos-provider service.
"""

import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

import chaos_provider

HELP = (
    "I am a fake provider: I call no model and answer as the question says. Ask 429 or 500 to fail every attempt, "
    "slow 20 to answer after 20 seconds, or 1x 500 to fail only the first attempt."
)


def user(text):
    return {"role": "user", "content": text}


def assistant(text):
    return {"role": "assistant", "content": text}


class ChaosProviderAnswers(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), chaos_provider.Handler)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def post(self, body):
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.server.server_port}/v1/chat/completions",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
        )
        return urllib.request.urlopen(request, timeout=10)

    def ask(self, *messages):
        with self.post({"model": "chaos", "messages": list(messages)}) as response:
            return json.load(response)["choices"][0]["message"]["content"]

    def test_the_first_answer_of_a_conversation_says_how_to_make_the_provider_fail(self):
        self.assertEqual(
            self.ask(user("What tickets do I have?")),
            "chaos answered: What tickets do I have?\n\n" + HELP,
        )

    def test_a_streamed_first_answer_says_it_too(self):
        with self.post({"model": "chaos", "stream": True, "messages": [user("Hello")]}) as response:
            chunks = [
                json.loads(line[len("data: "):])
                for line in response.read().decode().splitlines()
                if line.startswith("data: ") and line != "data: [DONE]"
            ]
        text = "".join(choice["delta"].get("content", "") for chunk in chunks for choice in chunk["choices"])
        self.assertEqual(text, "chaos answered: Hello\n\n" + HELP)

    def test_the_context_block_before_the_first_question_stays_out_of_the_answer(self):
        self.assertEqual(
            self.ask(user("<context>\nSigned in: Sam Support\n</context>\nHello again")),
            "chaos answered: Hello again\n\n" + HELP,
        )

    def test_a_later_answer_of_the_conversation_is_the_question_alone(self):
        self.assertEqual(
            self.ask(user("Hello there"), assistant("chaos answered: Hello there"), user("And now?")),
            "chaos answered: And now?",
        )

    def test_a_question_that_named_its_failure_is_answered_by_the_question_alone(self):
        with self.assertRaises(urllib.error.HTTPError) as failed:
            self.ask(user("1x 500 once"))
        self.assertEqual(failed.exception.code, 500)

        self.assertEqual(self.ask(user("1x 500 once")), "chaos answered: 1x 500 once")


if __name__ == "__main__":
    unittest.main()
