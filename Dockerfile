# syntax=docker/dockerfile:1
# Obraz audytora ogłoszeń o pracę. Bez ANTHROPIC_API_KEY działa w trybie offline (reguły),
# z kluczem włącza funkcje AI. Uruchamiany jako użytkownik bez uprawnień roota.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app
RUN useradd --create-home --uid 10001 app

COPY . .
RUN pip install .

# Pakiet jest zainstalowany w site-packages, więc katalogi z danymi wskazujemy jawnie.
ENV OGLOSZENIA_EXAMPLES_DIR=/app/examples

USER app
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health', timeout=4)"]

CMD ["uvicorn", "ogloszenia.api:app", "--host", "0.0.0.0", "--port", "8000"]
