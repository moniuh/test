# syntax=docker/dockerfile:1
# Obraz LMS szkoleń obowiązkowych. Bez ANTHROPIC_API_KEY działa w trybie offline (reguły),
# z kluczem włącza funkcje AI. Uruchamiany jako użytkownik bez uprawnień roota.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app
RUN useradd --create-home --uid 10001 app

# Jawna lista plików: obraz jest taki sam przy budowie z lokalnego katalogu i z repozytorium git.
COPY pyproject.toml README.md LICENSE.md ./
COPY src ./src
COPY data ./data
RUN pip install . \
 && rm -rf build src \
 && mkdir -p /data \
 && chown app:app /data

# Kod działa z site-packages (źródła usunięte po instalacji), więc katalogi z danymi wskazujemy jawnie.
ENV SZKOLENIA_DATA_DIR=/app/data \
    SZKOLENIA_DB=/data/szkolenia.db

# Trwałe dane (SQLite) — montuj tu wolumen, inaczej znikną razem z kontenerem.
VOLUME ["/data"]

USER app
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health', timeout=4)"]

CMD ["uvicorn", "szkolenia.api:app", "--host", "0.0.0.0", "--port", "8000"]
