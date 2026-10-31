#!/usr/bin/env bash
# Buduje frontend, osadza go w backendzie i tworzy jeden plik JAR z całą aplikacją.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Frontend"
(cd frontend && npm ci && npm run build)

echo "==> Osadzanie frontendu w backendzie"
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/. backend/src/main/resources/static/

echo "==> Backend"
(cd backend && mvn -B -q package -DskipTests)

JAR=$(ls backend/target/radar-pracy-*.jar | grep -v original | head -1)
echo "Gotowe: $JAR"
echo "Uruchom: java -jar $JAR   (aplikacja pod http://localhost:8080)"
