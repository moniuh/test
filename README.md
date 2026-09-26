# Skaner 3D (Android)

Aplikacja na Androida (testowana pod Samsung Galaxy S26 Ultra) do skanowania 3D z obrazu kamery.
Model zapisuje się w standardowych formatach: **PLY**, **OBJ**, **STL**.

## Jak to działa

1. **ARCore Depth API** – z obrazu kamery (głębia z ruchu, a na telefonach z czujnikiem ToF również z niego)
   ARCore wylicza mapę głębi dla każdej klatki oraz dokładną pozycję telefonu w przestrzeni.
2. **Fuzja TSDF** – kolejne mapy głębi (surowa głębia + mapa pewności) są łączone w rzadkiej siatce
   wokseli (bloki 8×8×8 alokowane tylko przy powierzchniach). Szum uśrednia się z wielu ujęć,
   a kolory pobierane są z kamery.
3. **Siatka (Surface Nets)** – przy zapisie z pola odległości generowana jest siatka trójkątów
   z normalnymi i kolorami wierzchołków; drobne „odpryski” szumu są usuwane.
4. **Eksport** do `Pobrane/Skaner3D/`:
   | Format | Zawartość | Otworzysz w |
   |---|---|---|
   | `.ply` (siatka) | wierzchołki + normalne + kolory + trójkąty (binarny) | MeshLab, Blender, CloudCompare, Windows 3D Viewer |
   | `.obj` | siatka z kolorami wierzchołków (`v x y z r g b`) | Blender, MeshLab, większość programów 3D |
   | `.stl` | siatka bez koloru (binarny) | slicery do druku 3D (Cura, PrusaSlicer, Bambu Studio) |
   | `_punkty.ply` | chmura punktów z normalnymi i kolorami | CloudCompare, MeshLab |

   Jednostki: metry, oś Y w górę.

## Instalacja

1. Wejdź na telefonie w zakładkę **Releases** tego repozytorium i pobierz najnowszy plik
   `Skaner3D-release.apk` (budowany automatycznie przez GitHub Actions z każdego pusha),
   ewentualnie pobierz artefakt `Skaner3D-apk` z zakładki **Actions**.
2. Zezwól na instalację z nieznanych źródeł i zainstaluj APK.
3. Przy pierwszym uruchomieniu aplikacja poprosi o dostęp do aparatu i – jeśli trzeba –
   o instalację „Usług Google Play dla AR” (ARCore).

## Skanowanie – wskazówki

- Naciśnij **Skanuj** i powoli obchodź obiekt dookoła, patrząc na niego z różnych wysokości.
  Zeskanowane powierzchnie pojawiają się jako niebieskawe punkty.
- Najlepsze wyniki: dobre oświetlenie, matowe powierzchnie z teksturą, odległość 0,3–1,5 m.
  Szkło, lustra i jednolite białe ściany skanują się słabo.
- **Ustawienia → Szczegółowość**: *Obiekt* (5 mm, małe przedmioty), *Standard* (1 cm),
  *Pomieszczenie* (2 cm, duże przestrzenie).
- **Zapisz** tworzy siatkę i zapisuje wybrane formaty; potem można je od razu udostępnić.

## Budowanie ze źródeł

```bash
./gradlew :core:test            # testy rekonstrukcji 3D (czysty Kotlin/JVM)
./gradlew :app:assembleRelease  # APK w app/build/outputs/apk/release/
```

Wymagania: JDK 17, Android SDK (compileSdk 35). Minimalny Android: 10 (API 29).

Struktura:
- `core/` – niezależna od Androida logika: fuzja TSDF, Surface Nets, czyszczenie siatki, eksport PLY/OBJ/STL (z testami).
- `app/` – aplikacja: sesja ARCore, pobieranie głębi/koloru, renderowanie podglądu (OpenGL ES), zapis przez MediaStore.

`app/debug.keystore` to celowo publiczny klucz testowy, dzięki któremu kolejne kompilacje z CI
instalują się jako aktualizacja. Nie używaj go do publikacji w Sklepie Play.
