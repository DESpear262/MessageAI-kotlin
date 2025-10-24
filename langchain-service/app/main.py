from typing import Any, Dict
import time
import hmac
import hashlib
import json

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .schemas import (
    AiRequestEnvelope,
    AiResponseEnvelope,
    MedevacTemplateData,
    SitrepTemplateData,
    ThreatsData,
    TasksData,
    IntentDetectData,
    CasevacWorkflowResponse,
    ConfidenceField,
    TemplateDocData,
)
from .providers import OpenAIProvider
from .firestore_client import FirestoreReader
from .rag import RAGCache
from .config import LANGCHAIN_SHARED_SECRET, SIGNATURE_MAX_AGE_SECONDS, LOG_LEVEL
import logging


logging.basicConfig(level=getattr(logging, LOG_LEVEL, logging.INFO))
logger = logging.getLogger("messageai")

app = FastAPI(title="MessageAI LangChain Service", version="0.1.0")
llm = OpenAIProvider()
fs = FirestoreReader()
rag = RAGCache(llm)


@app.middleware("http")
async def hmac_verification(request: Request, call_next):
    # Skip for health and docs
    if request.url.path in {"/healthz", "/docs", "/openapi.json"}:
        return await call_next(request)
    if not LANGCHAIN_SHARED_SECRET:
        return await call_next(request)
    sig = request.headers.get("x-sig")
    ts = request.headers.get("x-sig-ts")
    request_id = request.headers.get("x-request-id") or "unknown"
    if not sig or not ts or not request_id:
        return JSONResponse(status_code=401, content={"error": "Missing signature headers"})
    try:
        sig_time = int(ts)
    except ValueError:
        return JSONResponse(status_code=401, content={"error": "Invalid signature timestamp"})
    now = int(time.time() * 1000)
    age_seconds = (now - sig_time) / 1000
    if age_seconds > SIGNATURE_MAX_AGE_SECONDS:
        return JSONResponse(status_code=401, content={"error": "Signature expired"})

    body = await request.body()
    payload_hash = hashlib.sha256(body).hexdigest()
    base = f"{request_id}.{ts}.{payload_hash}"
    expected = hmac.new(LANGCHAIN_SHARED_SECRET.encode(), base.encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(sig, expected):
        logger.warning(json.dumps({"event": "invalid_signature", "request_id": request_id, "client": request.client.host}))
        return JSONResponse(status_code=401, content={"error": "Invalid signature"})
    return await call_next(request)


def _ok(request_id: str, data: Dict[str, Any]) -> JSONResponse:
    return JSONResponse(
        status_code=200,
        content=AiResponseEnvelope(requestId=request_id, status="ok", data=data).model_dump(by_alias=True),
    )


def _err(request_id: str, message: str, status: int = 500) -> JSONResponse:
    logger.error(json.dumps({"event": "error", "request_id": request_id, "message": message, "status": status}))
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
    rag.index_messages(messages)

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
    # Very lightweight heuristic: use RAG context to prime LLM (if enabled) for structured extraction in future
    rag.index_messages(_)
    data = ThreatsData(threats=[]).model_dump()
    return _ok(request_id, data)


@app.post("/sitrep/summarize")
def sitrep_summarize(body: AiRequestEnvelope):
    request_id = body.requestId
    payload = body.payload or {}
    time_window = payload.get("timeWindow", "6h")
    chat_id = (body.context or {}).get("chatId")
    msgs = fs.fetch_recent_messages(chat_id, limit=200)
    rag.index_messages(msgs)
    query = f"Summarize the last {time_window} of unit activity into a SITREP."
    context = rag.build_context(query)
    prompt = f"Context messages:\n{context}\n\nTask: {query}"
    # Markdown-only output; no PDFs
    summary = llm.chat(
        system_prompt="You summarize tactical chat into concise SITREPs in markdown.",
        user_prompt=prompt,
    )
    md = summary or "# SITREP\n\n- Time Window: {}\n".format(time_window)
    data = SitrepTemplateData(format="markdown", content=md, sections=[]).model_dump()
    return _ok(request_id, data)


# --- Template tools (markdown-only) -----------------------------------------
@app.post("/template/warnord")
def warnord_generate(body: AiRequestEnvelope):
    request_id = body.requestId
    # Use static markdown skeleton from repo as baseline
    md = _load_markdown_template("Input file templates/WARNO.md")
    data = TemplateDocData(templateType="WARNORD", content=md).model_dump()
    return _ok(request_id, data)


@app.post("/template/opord")
def opord_generate(body: AiRequestEnvelope):
    request_id = body.requestId
    md = _load_markdown_template("Input file templates/OPORD_Template.md")
    data = TemplateDocData(templateType="OPORD", content=md).model_dump()
    return _ok(request_id, data)


@app.post("/template/frago")
def frago_generate(body: AiRequestEnvelope):
    request_id = body.requestId
    md = _load_markdown_template("Input file templates/FRAGO.md")
    data = TemplateDocData(templateType="FRAGO", content=md).model_dump()
    return _ok(request_id, data)


def _load_markdown_template(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return "# Template\n\n_Template content unavailable in this build._"


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


@app.post("/tasks/extract")
def tasks_extract(body: AiRequestEnvelope):
    request_id = body.requestId
    data = TasksData(tasks=[]).model_dump()
    return _ok(request_id, data)


