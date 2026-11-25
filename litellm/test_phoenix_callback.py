"""The user and the session of phoenix_callback.py, as LiteLLM's spans carry them (chat ADR 0015).

Run in the LiteLLM image of compose.yaml by PhoenixCallbackTests. The request's user never reaches the model parameters
of a call, because config.yaml drops it before the provider, so the caller is the end user LiteLLM records. The span a
real LiteLLM exports is checked by CallerIdTests.
"""

import unittest

from litellm.integrations._types.open_inference import SpanAttributes

from phoenix_callback import _set_user_and_session

CALLER = "0f8fad5b-d9cb-469f-a165-70867728950e"
CONVERSATION = "7c9e6679-7425-40de-944b-e07fc1f90ae7"


class Span:
    def __init__(self):
        self.attributes = {}

    def set_attribute(self, key, value):
        self.attributes[key] = value


def call(end_user):
    return {
        "standard_logging_object": {
            "metadata": {
                "user_api_key_end_user_id": end_user,
                "requester_metadata": {"conversation_id": CONVERSATION},
            },
        }
    }


class UserAndSession(unittest.TestCase):
    def attributes(self, kwargs):
        span = Span()
        _set_user_and_session(span, kwargs)
        return span.attributes

    def test_the_caller_is_the_spans_user_and_the_conversation_its_session(self):
        self.assertEqual(
            self.attributes(call(CALLER)),
            {SpanAttributes.USER_ID: CALLER, SpanAttributes.SESSION_ID: CONVERSATION},
        )

    def test_a_request_without_an_end_user_has_no_user_on_its_span(self):
        self.assertEqual(self.attributes(call(None)), {SpanAttributes.SESSION_ID: CONVERSATION})


if __name__ == "__main__":
    unittest.main()
