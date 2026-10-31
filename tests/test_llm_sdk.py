"""Warstwa LLM z prawdziwym SDK, ale bez sieci (httpx2.MockTransport)."""
import json

import anthropic
import httpx2
import pytest

from asystent.answering import AIAnswer
from asystent.llm import ClaudeLLM, LLMUnavailable, ai_enabled, get_llm


def _client(handler) -> anthropic.Anthropic:
    return anthropic.Anthropic(api_key="test", max_retries=0,
                               http_client=httpx2.Client(transport=httpx2.MockTransport(handler)))


def _message(payload: dict | str, stop_reason: str = "end_turn") -> httpx2.Response:
    text = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False)
    return httpx2.Response(200, json={
        "id": "msg_test", "type": "message", "role": "assistant", "model": "test-model",
        "content": [{"type": "text", "text": text}], "stop_reason": stop_reason, "stop_sequence": None,
        "usage": {"input_tokens": 10, "output_tokens": 10},
    })


ANSWER = {"answer": "Do 28. dnia miesiąca.",
          "citations": [{"fragment_id": "F1", "quote": "najpóźniej 28. dnia miesiąca"}],
          "confidence": "wysoka", "needs_hr": False, "reason": "Wprost z § 4.", "sensitive_category": "brak",
          "injection_detected": False}


def test_structured_output_request_and_parse():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return _message(ANSWER)

    result = ClaudeLLM(client=_client(handler)).generate(system="sys", prompt="<pytanie>x</pytanie>", schema=AIAnswer)
    assert result.citations[0].quote == "najpóźniej 28. dnia miesiąca"
    body = seen["body"]
    assert body["output_config"]["format"]["type"] == "json_schema"
    assert "temperature" not in body
    assert body["messages"] == [{"role": "user", "content": "<pytanie>x</pytanie>"}]  # bez prefill


@pytest.mark.parametrize("stop_reason", ["refusal", "max_tokens"])
def test_unusable_stop_reason_raises(stop_reason):
    llm = ClaudeLLM(client=_client(lambda request: _message(ANSWER, stop_reason)))
    with pytest.raises(LLMUnavailable):
        llm.generate(system="s", prompt="p", schema=AIAnswer)


def test_api_error_raises_unavailable():
    llm = ClaudeLLM(client=_client(lambda request: httpx2.Response(500, json={"type": "error", "error": {
        "type": "api_error", "message": "boom"}})))
    with pytest.raises(LLMUnavailable, match="500"):
        llm.generate(system="s", prompt="p", schema=AIAnswer)


def test_assistant_end_to_end_with_sdk(make_assistant):
    def handler(request):
        prompt = json.loads(request.content)["messages"][0]["content"]
        fid = next(part.split('"')[1] for part in prompt.split("<fragment ")[1:] if "28. dnia" in part)
        return _message({**ANSWER, "citations": [{"fragment_id": fid, "quote": "najpóźniej 28. dnia miesiąca"}]})

    answer = make_assistant(llm=ClaudeLLM(client=_client(handler))).ask("Kiedy wypłata?")
    assert answer.source == "ai" and answer.status == "answered"
    assert answer.citations[0].doc_key == "regulamin-wynagradzania"


def test_assistant_falls_back_when_sdk_fails(make_assistant):
    llm = ClaudeLLM(client=_client(lambda request: httpx2.Response(529, json={"type": "error", "error": {
        "type": "overloaded_error", "message": "busy"}})))
    answer = make_assistant(llm=llm).ask("Kiedy wypłata?")
    assert answer.source == "offline" and answer.status == "answered"


def test_ai_enabled_flags(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "x")
    monkeypatch.setenv("HR_AI_OFFLINE", "1")
    assert not ai_enabled() and get_llm() is None
    monkeypatch.setenv("HR_AI_OFFLINE", "0")
    assert ai_enabled()
    monkeypatch.delenv("ANTHROPIC_API_KEY")
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    assert not ai_enabled()
