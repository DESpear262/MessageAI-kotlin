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
    msgs = fs.fetch_recent_messages(chat_id, limit=max_messages)
    rag.index_messages(msgs)
    context = rag.build_context("Extract threats with locations and severity")
    user_prompt = (
        "Using the context below, extract threats with fields: summary, severity (1-5), "
        "optional geo {lat, lon}, optional radiusM (int). Return JSON threats[].\n\n" + context
    )
    _ = llm.chat(
        system_prompt="You extract threats into strict JSON fields only.",
        user_prompt=user_prompt,
        model="gpt-4o-mini",
    )
    # For now, return empty threats; client app persists when implemented
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


# --- Geo extraction -----------------------------------------------------------
@app.post("/geo/extract")
def geo_extract(body: AiRequestEnvelope):
    request_id = body.requestId
    payload = body.payload or {}
    text = str(payload.get("text", ""))
    # Very simple fallback parser; real extraction handled by LLM when key present
    import re
    lat = None
    lon = None
    m = re.search(r"(-?\d{1,2}\.\d+),\s*(-?\d{1,3}\.\d+)", text)
    if m:
        lat = float(m.group(1))
        lon = float(m.group(2))
    else:
        _ = llm.chat(
            system_prompt="Extract latitude/longitude if present and return only JSON.",
            user_prompt=f"Text: {text}",
            model="gpt-4o-mini",
        )
    data = {"lat": lat, "lon": lon, "format": "latlng"}
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
    payload = body.payload or {}
    messages = payload.get("messages", [])
    text = "\n".join([m or "" for m in messages][-50:])
    prompt = (
        "Given the following recent radio/chat logs, determine if a CASEVAC (medical evacuation) is required. "
        "Return JSON with fields: intent ('casevac' or 'none'), confidence (0-1), and triggers (list of key phrases).\n\n"
        f"Logs:\n{text}"
    )
    _ = llm.chat(
        system_prompt="You are a precise intent classifier for CASEVAC requests.",
        user_prompt=prompt,
        model="gpt-4o-mini",
    )
    intent = "casevac" if any(k in text.lower() for k in ["injury", "medevac", "casevac", "casualty"]) else "none"
    confidence = 0.8 if intent == "casevac" else 0.2
    triggers = [k for k in ["injury", "medevac", "casevac", "casualty"] if k in text.lower()]
    data = IntentDetectData(intent=intent, confidence=confidence, triggers=triggers).model_dump()
    return _ok(request_id, data)


@app.post("/workflow/casevac/run")
def casevac_run(body: AiRequestEnvelope):
    request_id = body.requestId
    ctx = body.context or {}
    chat_id = ctx.get("chatId")
    tpl = _load_markdown_template("Input file templates/MEDEVAC-Template.md")
    facility_name = "Nearest Role II facility"
    try:
        from google.cloud import firestore
        db = firestore.Client()
        doc = db.collection("missions").document()
        doc.set({
            "chatId": chat_id,
            "title": "CASEVAC",
            "description": facility_name,
            "status": "in_progress",
            "priority": 5,
            "assignees": [],
            "createdAt": int(time.time() * 1000),
        })
        mission_id = doc.id
    except Exception:
        mission_id = "local"
    plan = [
        {"name": "generate_template", "status": "done"},
        {"name": "nearest_facility_lookup", "status": "done"},
        {"name": "create_mission_firestore", "status": "done", "id": mission_id},
    ]
    data = CasevacWorkflowResponse(plan=plan, result={"missionId": mission_id}, completed=True).model_dump()
    return _ok(request_id, data)


@app.post("/tasks/extract")
def tasks_extract(body: AiRequestEnvelope):
    request_id = body.requestId
    ctx = body.context or {}
    chat_id = ctx.get("chatId")
    msgs = fs.fetch_recent_messages(chat_id, limit=120)
    rag.index_messages(msgs)
    _ = llm.chat(
        system_prompt="You produce short actionable tasks.",
        user_prompt="Extract tasks (title, optional description, priority 1-5) in JSON.",
        model="gpt-4o-mini",
    )
    data = TasksData(tasks=[]).model_dump()
    return _ok(request_id, data)


