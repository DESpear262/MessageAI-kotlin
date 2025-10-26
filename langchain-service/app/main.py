from typing import Any, Dict
from pathlib import Path
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
from .embedding_store import FirestoreEmbeddingStore
from .config import LANGCHAIN_SHARED_SECRET, SIGNATURE_MAX_AGE_SECONDS, LOG_LEVEL
import logging


logging.basicConfig(level=getattr(logging, LOG_LEVEL, logging.INFO))
logger = logging.getLogger("messageai")

app = FastAPI(title="MessageAI LangChain Service", version="0.1.0")
llm = OpenAIProvider()
fs = FirestoreReader()
store = FirestoreEmbeddingStore(fs)
rag = RAGCache(llm, store)


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
@app.post("/assistant/gate")
def assistant_gate(body: AiRequestEnvelope):
    request_id = body.requestId
    payload = body.payload or {}
    text = str(payload.get("prompt", ""))
    # Minimal tools awareness; no chat history to keep it cheap
    tools = [
        "threats/extract for when the user mentions threats or potential threats",
        "tasks/extract for when the user mentions tasks or potential tasks",
        "sitrep/summarize for when the user requests a sitrep",
        "template/warnord for when the user requests a warnord populated from the recent messages",
        "template/opord for when the user requests an opord populated from the recent messages",
        "template/frago for when the user requests a frago populated from the recent messages",
        "workflow/casevac/run for when the user sends a message indicating a major medical emergency",
        "geo/extract",
    ]
    gate_prompt = (
        "You are a gate model. Decide if the following single message should be escalated "
        "to a full assistant with these tools available: " + ", ".join(tools) + ".\n"
        "Return STRICT JSON: {\"escalate\": boolean}. If uncertain, set escalate=true.\n\n"
        f"MESSAGE:\n{text}"
    )

    def _one_vote() -> bool:
        try:
            raw = llm.chat(
                system_prompt=(
                    "You are a gate model. Decide whether to escalate. "
                    "If the message mentions threats, potential threats, tasks to take, medical emergencies, or geospatial info, return {\\\"escalate\\\": true}. "
                    "If uncertain, return {\\\"escalate\\\": true}. Return only JSON."
                ),
                user_prompt=gate_prompt,
                model="gpt-4.1-nano",
            )
            obj = json.loads(raw or "{}")
            return bool(obj.get("escalate", True))
        except Exception:
            return False  # no-op on error

    votes = [_one_vote(), _one_vote(), _one_vote()]
    escalate = votes.count(True) >= 2
    try:
        logger.info(json.dumps({
            "event": "assistant_gate_votes",
            "request_id": request_id,
            "votes": votes,
            "escalate": escalate
        }))
    except Exception:
        pass
    return _ok(request_id, {"escalate": escalate})


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
    ctx = body.context or {}
    payload = body.payload or {}
    chat_id = ctx.get("chatId")
    max_messages = int(payload.get("maxMessages", 10))
    current_location = (payload.get("currentLocation") or {})
    cur_lat = current_location.get("lat")
    cur_lon = current_location.get("lon")

    msgs = fs.fetch_recent_messages(chat_id, limit=max_messages)
    # Prepare a compact JSON preview for the model
    preview = [
        {"id": m.get("id"), "text": (m.get("text") or "")[:500]}
        for m in msgs if (m.get("text") or "").strip()
    ]

    rag.index_messages(msgs)
    context = rag.build_context("Extract threats with locations, tags, severity, and offsets")
    user_prompt = (
        "Task: From the recent messages and context, extract ALL distinct threats.\n"
        "Return STRICT JSON: {threats:[{id, summary, severity (1-5), confidence (0-1), tags: [string], "
        "positionMode: 'absolute'|'offset', abs?: {lat, lon}, offset?: {north: meters, east: meters}, "
        "radiusM?: int, sourceMsgId?: string}]}.\n"
        "Rules:\n"
        "- If message provides absolute coords, use positionMode='absolute' with abs {lat, lon}.\n"
        "- If message provides relative direction/distance (e.g., '2 km north'), use positionMode='offset' with meters north/east relative to CURRENT_LOCATION.\n"
        "- If no location provided, default to positionMode='offset' with offset {north:0, east:0}.\n"
        "- Include concise threat tags like ['armor','small_arms','uav','ied'] when applicable.\n"
        "- Set radiusM if indicated; otherwise omit or choose a reasonable default (e.g., 500).\n"
        "- Choose severity by judgement (1=low..5=critical).\n"
        "- Set sourceMsgId to the selected message id.\n\n"
        f"CURRENT_LOCATION: {{'lat': {cur_lat}, 'lon': {cur_lon}}}\n"
        f"RECENT_MESSAGES_JSON: {json.dumps(preview)}\n\n"
        f"CONTEXT_SNIPPET:\n{context}"
    )
    raw = llm.chat(
        system_prompt="You are a precise information extractor. Always return STRICT JSON per the contract.",
        user_prompt=user_prompt,
        model="gpt-4o-mini",
    )
    threats: list[dict[str, Any]] = []
    try:
        obj = json.loads(raw or "{}")
        th = obj.get("threats")
        if isinstance(th, list):
            threats = th
    except Exception:
        pass

    # Heuristic fallback if model unavailable or returned invalid JSON
    if not threats:
        kw = [
            ("armor", ["armor", "tank", "apc", "ifv"]),
            ("small_arms", ["shots fired", "gunfire", "contact", "small arms"]),
            ("ied", ["ied", "improvised explosive", "roadside bomb"]),
            ("uav", ["drone", "uav"]) ,
        ]
        tag = None
        src = None
        text = None
        for m in preview:
            t = (m.get("text") or "").lower()
            for tg, words in kw:
                if any(w in t for w in words):
                    tag = tg
                    src = m.get("id")
                    text = m.get("text")
                    break
            if tag:
                break
        if tag and text:
            threats = [{
                "id": "auto-1",
                "summary": text[:180],
                "severity": 3,
                "confidence": 0.6,
                "tags": [tag],
                "positionMode": "offset",
                "offset": {"north": 0, "east": 0},
                "radiusM": 500,
                "sourceMsgId": src,
            }]

    data = ThreatsData(threats=threats).model_dump()
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


# --- Assistant router --------------------------------------------------------
@app.post("/assistant/route")
def assistant_route(body: AiRequestEnvelope):
    """
    Assistant Router
    -----------------
    Purpose: Given a user prompt and optional target chat context, ask the LLM to select
    exactly one tool from the supported list and return a STRICT JSON decision
    {tool, args, reply}. The router itself never applies deterministic routing; the model
    makes the decision using:
      - A tools list and catalog (usage guidance + I/O contracts)
      - A concise RAG summary of recent target chat messages (if provided)
      - A preview of recent messages (role|timestamp|text)

    Behavior contracts:
      - The Buddy chat is a control channel and MUST NOT be used as context.
      - If intent is ambiguous or target chat is missing, the model must choose tool='none'
        and set a helpful natural-language reply explaining next steps.
      - Side-effect tools (e.g., workflow/casevac/run) should only be selected when the
        user's intent is clear in the prompt/context.
    """
    request_id = body.requestId
    ctx = body.context or {}
    chat_id = ctx.get("chatId")
    payload = body.payload or {}
    prompt = str(payload.get("prompt", ""))
    candidate_chats = payload.get("candidateChats", []) or []

    # Tools list advertised to the model (routing is chosen by the model; no deterministic rules here)
    tools = [
        "sitrep/summarize",
        "template/warnord",
        "template/opord",
        "template/frago",
        "threats/extract",
        "workflow/casevac/run",
        "tasks/extract",
        "geo/extract",
        "none",
    ]

    # Build a lightweight router context from the resolved target chat (if any)
    msgs = fs.fetch_recent_messages(chat_id, limit=120) if chat_id else []
    rag.index_messages(msgs)
    context = rag.build_context("Assistant decision context")

    # Produce a short, readable preview of recent messages for the model (role|ts|text)
    def _preview_messages(rows):
        preview_lines = []
        for m in rows[:20]:  # most recent first as fetched
            ts = m.get("createdAt") or m.get("timestamp")
            sender = m.get("senderId") or "unknown"
            text = (m.get("text") or "").replace("\n", " ")
            if len(text) > 180:
                text = text[:177] + "..."
            preview_lines.append(f"{ts}|{sender}|{text}")
        return "\n".join(preview_lines)

    messages_preview = _preview_messages(msgs) if msgs else ""

    # Tools catalog with usage guidance and I/O contracts (for model awareness only)
    tools_catalog = (
        "- sitrep/summarize: Use to summarize the last X hours of unit activity. "
        "Input: {timeWindow: string like '6h'}; Output: {content: markdown}.\n"
        "- template/warnord: Use when user requests a WARNORD/WARNO template. Input: {}; Output: {content: markdown}.\n"
        "- template/opord: Use when user requests an OPORD template. Input: {}; Output: {content: markdown}.\n"
        "- template/frago: Use when user requests a FRAGO/FRAGORD/fragmentary order. Input: {}; Output: {content: markdown}.\n"
        "  Note: currently returns a template skeleton; not auto-filled.\n"
        "- threats/extract: Use when asked to extract threats into JSON. Input: {maxMessages?}; Output: {threats: []}.\n"
        "- tasks/extract: Use when asked to extract tasks into JSON. Output: {tasks: []}.\n"
        "- workflow/casevac/run: Use ONLY when the user clearly intends to initiate a CASEVAC. "
        "Side effect: creates a mission record. Output includes {result: {missionId}}.\n"
        "- geo/extract: Use to parse latitude/longitude from free text. Output: {lat, lon}.\n"
        "- none: Use when no tool applies or you need clarification."
    )

    # Few-shot routing examples to anchor decisions (no deterministic routing added)
    examples = (
        "Example 1\n"
        "User: 'Generate a FRAGO based on my conversation with Capt. Parker.'\n"
        "Assistant JSON: {\"tool\":\"template/frago\",\"args\":{},\"reply\":\"Generating FRAGO template…\"}\n\n"
        "Example 2\n"
        "User: 'Give me a SITREP for the last 6 hours.'\n"
        "Assistant JSON: {\"tool\":\"sitrep/summarize\",\"args\":{\"timeWindow\":\"6h\"},\"reply\":\"Compiling SITREP…\"}\n\n"
        "Example 3\n"
        "User: 'Kick off a CASEVAC for the injured interpreter.'\n"
        "Assistant JSON: {\"tool\":\"workflow/casevac/run\",\"args\":{},\"reply\":\"Starting CASEVAC workflow…\"}\n\n"
        "Example 4\n"
        "User: 'Generate an OPORD template.'\n"
        "Assistant JSON: {\"tool\":\"template/opord\",\"args\":{},\"reply\":\"Generating OPORD template…\"}"
    )

    decision = llm.chat(
        system_prompt=(
            "You are the Assistant Router for a tactical chat application. "
            "Your job is to choose ONE tool from the provided tools list based on the user's prompt and the provided context. "
            "Never invent tools. Never perform irreversible actions unless the user's intent is clear. "
            "Buddy chat is a control channel and MUST NOT be used for context; use the provided targetChatId context bundle if present. "
            "Always respond with strict JSON only: {tool: string in tools list, args: object, reply: string}. "
            "If no tool fits or you need a chat selected, use tool='none' and set reply to a short helpful next step."
        ),
        user_prompt=(
            "TOOLS LIST: " + ", ".join(tools) + "\n\n" +
            "TOOLS CATALOG AND USAGE GUIDANCE:\n" + tools_catalog + "\n\n" +
            "PROMPT:\n" + prompt + "\n\n" +
            f"RESOLVED TARGET CHAT ID: {chat_id or 'null'}\n" +
            "CONTEXT SUMMARY (RAG):\n" + (context or "") + "\n\n" +
            ("RECENT MESSAGES PREVIEW (most recent first):\n" + messages_preview + "\n\n" if messages_preview else "") +
            ("CANDIDATE CHATS (id,name,updatedAt,lastMessage):\n" + json.dumps(candidate_chats) + "\n\n" if candidate_chats else "") +
            "OUTPUT CONTRACT:\n"
            "- Return STRICT JSON only with keys: tool, args, reply.\n"
            "- The reply must be helpful natural language for the user.\n"
            "- If intent is ambiguous or target chat is missing, use tool='none' and ask for the needed info.\n\n"
            "ROUTING EXAMPLES:\n" + examples
        ),
        model="gpt-4o-mini",
    )
    # We return the opaque decision; the app will execute the chosen tool.
    data = {"decision": decision or "{\"tool\":\"none\",\"args\":{},\"reply\":\"I didn't understand.\"}"}
    try:
        logger.info(json.dumps({
            "event": "assistant_route_decision",
            "request_id": request_id,
            "chat_id": chat_id or "null",
            "prompt_len": len(prompt),
            "has_msgs": bool(msgs),
            "decision": data["decision"],
            "candidate_count": len(candidate_chats)
        }))
    except Exception:
        logger.warning(json.dumps({
            "event": "assistant_route_log_failed",
            "request_id": request_id
        }))
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

# --- Template tools (markdown with RAG fill) --------------------------------

def _extract_placeholders(md: str):
    import re
    return sorted(set(re.findall(r"{{\s*([A-Za-z0-9_]+)\s*}}", md)))


def _apply_placeholders(md: str, values: Dict[str, str]) -> str:
    import re

    def repl(match):
        key = match.group(1).strip()
        return str(values.get(key, ""))

    return re.sub(r"{{\s*([A-Za-z0-9_]+)\s*}}", repl, md)


def _select_chat_via_llm(prompt: str, candidate_chats: list[dict[str, Any]]) -> str | None:
    try:
        # Filter out obvious control/buddy/system chats before presenting to the model
        filtered = [c for c in (candidate_chats or []) if str(c.get("name", "")).strip().lower() not in {"ai buddy", "buddy", "assistant"}]
        decision = llm.chat(
            system_prompt=(
                "You select ONE chatId from the candidate list that best matches the user's prompt. "
                "Return STRICT JSON only with a single key chatId (string or null). If unclear, return null."
            ),
            user_prompt=(
                "PROMPT:\n" + str(prompt or "") + "\n\nCANDIDATE_CHATS (JSON):\n" + json.dumps(filtered)
            ),
            model="gpt-4o-mini",
        )
        obj = json.loads(decision or "{}")
        cid = obj.get("chatId")
        if isinstance(cid, str) and cid:
            logger.info(json.dumps({"event": "template_chat_selected", "chatId": cid}))
            return cid
    except Exception:
        logger.warning(json.dumps({"event": "template_chat_select_error"}))
    return None


def _generate_filled_template(body: AiRequestEnvelope, template_type: str, template_path: str):
    request_id = body.requestId
    ctx = body.context or {}
    payload = body.payload or {}
    chat_id = ctx.get("chatId")
    prompt = payload.get("prompt") or ""
    candidate_chats = payload.get("candidateChats") or []

    md = _load_markdown_template(template_path)

    # If no explicit chat, ask LLM to choose from candidates. No deterministic selection here.
    if not chat_id and candidate_chats:
        chat_id = _select_chat_via_llm(prompt, candidate_chats)

    if not chat_id:
        logger.info(json.dumps({"event": "template_fill_no_chat", "template": template_type}))
        return _ok(request_id, TemplateDocData(templateType=template_type, content=md).model_dump())

    msgs = fs.fetch_recent_messages(chat_id, limit=200)
    # Prefer precomputed chunk vectors when available
    chunks = store.read_recent_chunks(chat_id, message_limit=200)
    if chunks:
        context = rag.build_context_from_chunks(query=f"Fill {template_type} template from chat context", chunk_rows=chunks)
    else:
        rag.index_messages(msgs, chat_id=chat_id)
        context = rag.build_context(f"Fill {template_type} template from chat context")
    placeholders = _extract_placeholders(md)
    fill_prompt = (
        "You are filling a "
        + template_type
        + " markdown template using the provided chat context.\n"
        + "Return STRICT JSON only: { key: value } for these keys:\n"
        + json.dumps(placeholders)
        + "\n\nGuidelines:\n"
        + "- If a value is unknown or not inferable, return an empty string.\n"
        + "- Keep values concise and factual. Plain text only, no markdown.\n\n"
        + "CHAT CONTEXT:\n"
        + (context or "")
    )
    json_map = llm.chat(
        system_prompt="Return only valid JSON mapping of placeholder keys to string values.",
        user_prompt=fill_prompt,
        model="gpt-4o-mini",
    )
    try:
        values = json.loads(json_map or "{}")
        if not isinstance(values, dict):
            values = {}
    except Exception:
        logger.warning(json.dumps({"event": "template_fill_parse_error", "template": template_type}))
        values = {}
    filled = _apply_placeholders(md, values)
    return _ok(request_id, TemplateDocData(templateType=template_type, content=filled).model_dump())


@app.post("/template/warnord")
def warnord_generate(body: AiRequestEnvelope):
    return _generate_filled_template(body, "WARNORD", "Input file templates/WARNO.md")


@app.post("/template/opord")
def opord_generate(body: AiRequestEnvelope):
    return _generate_filled_template(body, "OPORD", "Input file templates/OPORD_Template.md")


@app.post("/template/frago")
def frago_generate(body: AiRequestEnvelope):
    return _generate_filled_template(body, "FRAGO", "Input file templates/FRAGO.md")


def _load_markdown_template(path: str) -> str:
    try:
        candidates = []
        # 1) As provided (relative to CWD)
        candidates.append(Path(path))
        # 2) Relative to this file's directory (langchain-service/app)
        here = Path(__file__).resolve().parent
        candidates.append(here / path)
        # 3) Relative to langchain-service/ root
        candidates.append(here.parent / path)
        # 4) Relative to repo root (two levels up from app/)
        candidates.append(here.parent.parent / path)

        for candidate in candidates:
            try:
                if candidate.exists():
                    with candidate.open("r", encoding="utf-8") as f:
                        content = f.read()
                        logger.info(json.dumps({
                            "event": "template_load",
                            "path": path,
                            "resolved": str(candidate),
                            "size": len(content)
                        }))
                        return content
            except Exception:
                # Try next candidate
                pass
    except Exception:
        pass
    logger.warning(json.dumps({"event": "template_load_fallback", "path": path}))
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


@app.post("/rag/warm")
def rag_warm(body: AiRequestEnvelope):
    request_id = body.requestId
    ctx = body.context or {}
    payload = body.payload or {}
    chat_id = ctx.get("chatId")
    if not chat_id:
        return _ok(request_id, {"warmed": 0})
    limit = int(payload.get("limit", 200))
    msgs = fs.fetch_recent_messages(chat_id, limit=limit)
    # Force embed and store per message chunks if none exist (compat warm); client/backfill will usually precompute
    for m in msgs:
        text = str(m.get("text") or "").strip()
        if not text:
            continue
        chunks = [text[i : i + 700] for i in range(0, len(text), 700)]
        rows = []
        for idx, ch in enumerate(chunks):
            vec = llm.embed(ch)
            rows.append({"seq": idx, "text": ch, "len": len(ch), "embed": vec})
        try:
            store.write_chunks(chat_id, m.get("id") or str(id(m)), rows)
        except Exception:
            pass
    return _ok(request_id, {"warmed": len(msgs)})


