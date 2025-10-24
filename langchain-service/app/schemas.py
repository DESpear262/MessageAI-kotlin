from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class AiRequestEnvelope(BaseModel):
    requestId: str = Field(..., alias="requestId")
    context: Dict[str, Any] = {}
    payload: Dict[str, Any] = {}


class AiResponseEnvelope(BaseModel):
    requestId: str
    status: str
    data: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


# Template contracts driven by Input file templates

class ConfidenceField(BaseModel):
    value: Optional[str] = None
    confidence: float = 0.0
    required: bool = False


class MedevacTemplateData(BaseModel):
    templateType: str = "MEDEVAC"
    fields: Dict[str, ConfidenceField]
    missing: List[str] = []


class TemplateDocData(BaseModel):
    templateType: str
    format: str = "markdown"
    content: str


class SitrepTemplateData(BaseModel):
    format: str = "markdown"
    content: str
    sections: Optional[List[Dict[str, str]]] = None


class ThreatItem(BaseModel):
    id: str
    summary: str
    severity: int = 3
    confidence: float = 0.5
    geo: Optional[Dict[str, Any]] = None
    radiusM: Optional[int] = None
    ts: Optional[str] = None


class ThreatsData(BaseModel):
    threats: List[ThreatItem]


class IntentDetectData(BaseModel):
    intent: str
    confidence: float
    triggers: List[str] = []


class CasevacPlanStep(BaseModel):
    name: str
    status: str
    detail: Optional[Dict[str, Any]] = None


class CasevacWorkflowResponse(BaseModel):
    plan: List[CasevacPlanStep]
    result: Dict[str, Any]
    completed: bool = False


# Tasks extraction
class TaskItem(BaseModel):
    title: str
    description: Optional[str] = None
    priority: Optional[int] = None
    assignees: Optional[List[str]] = None
    dueAt: Optional[str] = None
    sourceMsgId: Optional[str] = None


class TasksData(BaseModel):
    tasks: List[TaskItem]


