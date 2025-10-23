from typing import Any, Dict
import time

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .schemas import (
    AiRequestEnvelope,
    AiResponseEnvelope,
    MedevacTemplateData,
    SitrepTemplateData,
    ThreatsData,
    IntentDetectData,
    CasevacWorkflowResponse,
    ConfidenceField,
)
from .providers import OpenAIProvider
from .firestore_client import FirestoreReader


app = FastAPI(title="MessageAI LangChain Service", version="0.1.0")
llm = OpenAIProvider()
fs = FirestoreReader()


def _ok(request_id: str, data: Dict[str, Any]) -> JSONResponse:
    return JSONResponse(
        status_code=200,
        content=AiResponseEnvelope(requestId=request_id, status="ok", data=data).model_dump(by_alias=True),
    )


def _err(request_id: str, message: str, status: int = 500) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content=AiResponseEnvelope(requestId=request_id, status="error", error=message).model_dump(by_alias=True),
    )


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


@app.post("/template/generate")
def generate_template(body: AiRequestEnvelope):
    request_id = body.requestId
    payload = body.payload or {}
    template_type = str(payload.get("type", "MEDEVAC")).upper()
    chat_id = (body.context or {}).get("chatId")
    messages = fs.fetch_recent_messages(chat_id, limit=int(payload.get("maxMessages", 50)))

    # Build minimal MEDEVAC fields from template file definitions
    required_fields = [
        "Pickup_Site_Location",
        "Radio_Frequency_Call_Sign_Suffix",
        "Patients_By_Precedence",
        "Special_Equipment_Required",
        "Patients_By_Type",
        "Pickup_Site_Security_Or_Injury_Type",
        "Marking_Method",
        "Patient_Nationality_Status",
        "NBC_Contamination_Or_Terrain",
    ]
    fields = {k: ConfidenceField(required=True).model_dump() for k in required_fields}

    data = MedevacTemplateData(fields=fields).model_dump()
    return _ok(request_id, data)


@app.post("/threats/extract")
def threats_extract(body: AiRequestEnvelope):
    request_id = body.requestId
    chat_id = (body.context or {}).get("chatId")
    max_messages = int((body.payload or {}).get("maxMessages", 100))
    _ = fs.fetch_recent_messages(chat_id, limit=max_messages)
    # Placeholder response
    data = ThreatsData(threats=[]).model_dump()
    return _ok(request_id, data)


@app.post("/sitrep/summarize")
def sitrep_summarize(body: AiRequestEnvelope):
    request_id = body.requestId
    payload = body.payload or {}
    time_window = payload.get("timeWindow", "6h")
    chat_id = (body.context or {}).get("chatId")
    _ = fs.fetch_recent_messages(chat_id, limit=200)
    # Minimal sectioned markdown
    md = "# SITREP\n\n- Time Window: {}\n".format(time_window)
    data = SitrepTemplateData(format="markdown", content=md, sections=[]).model_dump()
    return _ok(request_id, data)


@app.post("/intent/casevac/detect")
def casevac_detect(body: AiRequestEnvelope):
    request_id = body.requestId
    # Basic placeholder
    data = IntentDetectData(intent="none", confidence=0.1, triggers=[]).model_dump()
    return _ok(request_id, data)


@app.post("/workflow/casevac/run")
def casevac_run(body: AiRequestEnvelope):
    request_id = body.requestId
    plan = [
        {"name": "generate_meDevac", "status": "done"},
        {"name": "nearest_facility_lookup", "status": "pending"},
    ]
    data = CasevacWorkflowResponse(plan=plan, result={}, completed=False).model_dump()
    return _ok(request_id, data)


