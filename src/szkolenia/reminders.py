"""Planowanie przypomnień i eskalacji. Idempotencję zapewnia stały `dedup_key` na etap."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from typing import Literal

from .textutil import polish_plural

MessageKind = Literal["reminder", "overdue", "escalation"]

KIND_LABELS = {
    "reminder": "przypomnienie",
    "overdue": "po terminie",
    "escalation": "eskalacja do przełożonego",
}


@dataclass(frozen=True)
class ReminderSettings:
    # Dni przed terminem, w których wysyłamy przypomnienie (każdy etap najwyżej raz).
    offsets: tuple[int, ...] = (30, 7, 1)
    # Eskalacja do przełożonego, gdy opóźnienie osiągnie tyle dni.
    escalate_after_days: int = 1
    hr_name: str = "Dział HR"
    hr_email: str = "hr@firma-demo.example"


@dataclass(frozen=True)
class ReminderContext:
    assignment_id: int
    employee_name: str
    employee_email: str
    department: str
    training_title: str
    due_date: date
    manager_name: str | None = None
    manager_email: str | None = None


@dataclass(frozen=True)
class PlannedMessage:
    dedup_key: str
    kind: MessageKind
    recipient_name: str
    recipient_email: str
    subject: str
    body: str
    meta: dict = field(default_factory=dict, compare=False)


def fmt_date(d: date) -> str:
    return d.strftime("%d.%m.%Y")


def _days(n: int) -> str:
    return f"{n} {polish_plural(n, 'dzień', 'dni', 'dni')}"


def reminder_stage(days_left: int, offsets: tuple[int, ...]) -> int | None:
    """Najbliższy etap przypomnienia dla liczby dni do terminu (np. 5 dni → etap 7).

    Pominięte wcześniejsze etapy (np. system był wyłączony) nie są wysyłane wstecz.
    """
    if days_left < 0:
        return None
    for offset in sorted(offsets):
        if days_left <= offset:
            return offset
    return None


def plan_messages(ctx: ReminderContext, today: date, settings: ReminderSettings) -> list[PlannedMessage]:
    days_left = (ctx.due_date - today).days
    prefix = f"a{ctx.assignment_id}"
    due = fmt_date(ctx.due_date)
    messages: list[PlannedMessage] = []

    stage = reminder_stage(days_left, settings.offsets)
    if stage is not None:
        when = "dziś" if days_left == 0 else f"za {_days(days_left)}"
        messages.append(PlannedMessage(
            dedup_key=f"{prefix}:przed:{stage}",
            kind="reminder",
            recipient_name=ctx.employee_name,
            recipient_email=ctx.employee_email,
            subject=f"Przypomnienie: szkolenie „{ctx.training_title}” — termin {due}",
            body=(
                f"Dzień dobry,\n\nprzypominamy o szkoleniu „{ctx.training_title}”. "
                f"Termin ukończenia mija {when} ({due}).\n"
                "Szkolenie znajdziesz w zakładce „Moje szkolenia”.\n\nDział HR"
            ),
        ))

    if days_left < 0:
        late = -days_left
        messages.append(PlannedMessage(
            dedup_key=f"{prefix}:po_terminie",
            kind="overdue",
            recipient_name=ctx.employee_name,
            recipient_email=ctx.employee_email,
            subject=f"Po terminie: szkolenie „{ctx.training_title}”",
            body=(
                f"Dzień dobry,\n\ntermin szkolenia „{ctx.training_title}” minął {due} "
                f"(opóźnienie: {_days(late)}). Ukończ je jak najszybciej — "
                "informacja trafi też do przełożonego.\n\nDział HR"
            ),
        ))
        if late >= settings.escalate_after_days:
            to_name = ctx.manager_name or settings.hr_name
            to_email = ctx.manager_email or settings.hr_email
            messages.append(PlannedMessage(
                dedup_key=f"{prefix}:eskalacja",
                kind="escalation",
                recipient_name=to_name,
                recipient_email=to_email,
                subject=f"Eskalacja: {ctx.employee_name} — „{ctx.training_title}” po terminie",
                body=(
                    f"Dzień dobry,\n\n{ctx.employee_name} ({ctx.department}) nie ukończył(a) "
                    f"szkolenia „{ctx.training_title}” w terminie {due} "
                    f"(opóźnienie: {_days(late)}). Prosimy o dopilnowanie ukończenia.\n\n"
                    "Wiadomość wygenerowana automatycznie przez system ewidencji szkoleń."
                ),
            ))
    return messages
