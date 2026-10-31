"""Warstwa LLM bez sieci: SDK z `httpx2.MockTransport` sprawdza format żądań i obsługę błędów."""
import json

import anthropic
import httpx2
import pytest

from symulator.candidate import CandidateReplyAI
from symulator.coach import CoachAIVerdict
from symulator.llm import ClaudeLLM, LLMUnavailable, get_llm
from symulator.report import ReportNotesAI

SAMPLES = {
    CandidateReplyAI: {"answer": "Dzień dobry.", "revealed_fact_ids": ["kom_s"], "mentioned_trap_ids": [], "emotion": "neutralna"},
    CoachAIVerdict: {"question_type": "otwarte", "illegal": False, "category": "brak", "explanation": "", "suggestion": "", "tip": "Dopytaj."},
    ReportNotesAI: {"notes": [{"kind": "do_poprawy", "observation": "O", "quote": "Q", "suggestion": "S"}]},
}


def _llm(handler) -> ClaudeLLM:
    client = anthropic.Anthropic(api_key="test", max_retries=0, http_client=httpx2.Client(transport=httpx2.MockTransport(handler)))
    return ClaudeLLM(client=client)


def _message(body: dict, payload: dict, stop_reason: str = "end_turn") -> httpx2.Response:
    return httpx2.Response(200, json={
        "id": "msg_test", "type": "message", "role": "assistant", "model": body["model"],
        "content": [{"type": "text", "text": json.dumps(payload, ensure_ascii=False)}],
        "stop_reason": stop_reason, "stop_sequence": None,
        "usage": {"input_tokens": 1, "output_tokens": 1},
    })


@pytest.mark.parametrize("schema", list(SAMPLES))
def test_schemas_roundtrip_through_sdk(schema):
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return _message(seen["body"], SAMPLES[schema])

    result = _llm(handler).generate(system="system", prompt="<pytanie>x</pytanie>", schema=schema, effort="low")
    assert isinstance(result, schema)
    body = seen["body"]
    assert body["output_config"]["format"]["type"] == "json_schema"
    assert body["output_config"]["effort"] == "low"
    assert "temperature" not in body
    assert body["messages"][-1]["role"] == "user"  # bez prefillu asystenta


def test_api_error_maps_to_llm_unavailable():
    llm = _llm(lambda request: httpx2.Response(500, json={"type": "error", "error": {"type": "api_error", "message": "boom"}}))
    with pytest.raises(LLMUnavailable):
        llm.generate(system="s", prompt="p", schema=CoachAIVerdict)


def test_refusal_maps_to_llm_unavailable():
    llm = _llm(lambda request: _message(json.loads(request.content), SAMPLES[CoachAIVerdict], stop_reason="refusal"))
    with pytest.raises(LLMUnavailable):
        llm.generate(system="s", prompt="p", schema=CoachAIVerdict)


def test_offline_flag_disables_ai(monkeypatch):
    monkeypatch.setenv("HR_AI_OFFLINE", "1")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test")
    assert get_llm() is None
