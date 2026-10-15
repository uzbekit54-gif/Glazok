# Текущее состояние системы (SYSTEM_STATE.md)

Последнее обновление: 2026-10-15  
Ответственный: `@Architecture-Guardian`  
Статус проекта: **Инициализация архитектурного каркаса и промпта**

---

## 1. Матрица готовности модулей (Module Readiness Matrix)

| Модуль | Ответственный субагент | Статус | Примечания |
|---|---|---|---|
| **Core Architecture & Contracts** | `@Architecture-Guardian` | `COMPLETED` | Описаны контракты, ADR (001-005) и C4 диаграммы |
| **Smart Price Tag Parser** | `@Smart-Parser-Engineer` | `COMPLETED` | Двухшаговый алгоритм (Spatial + Regex), 100% тестов пройдены |
| **Domain Models & Matcher** | `@Smart-Parser-Engineer` | `COMPLETED` | Модуль `:core:domain` (NormalizedRect, RawTextBlock, PriceTag) |
| **CameraX Stream Ingestion** | `@Vision-Core-Engineer` | `COMPLETED` | Релизован `CameraManager` с `STRATEGY_KEEP_ONLY_LATEST` |
| **ML Kit Object & Text (Tier 1)** | `@Vision-Core-Engineer` | `COMPLETED` | Интегрированы ML Kit Objects (Stream Mode) + Text OCR |
| **Deep VLM On-Demand (Tier 2)** | `@Vision-Core-Engineer` | `COMPLETED` | Реализован `GeminiVlmService` с жестом Double-Tap и карточкой |
| **Spatial Matcher & Debounce** | `@Smart-Parser-Engineer` | `COMPLETED` | Реализована привязка ценников к DetectedItem по расстоянию |
| **Audio TTS & Haptic Arbiter** | `@Audio-Haptics-Specialist` | `COMPLETED` | Реализован `SpeechQueueArbiter` с дебаунсом 5с и `HapticManager` |
| **Compose Universal HUD** | `@Compose-HUD-Engineer` | `COMPLETED` | Реализован `GlazokCameraScreen`, Canvas HUD и плавающий ControlBar |
| **QA Benchmarks & Unit Tests** | `@QA-Benchmark-Engineer` | `VERIFIED` | Успешное живое тестирование на смартфоне Xiaomi (ID: d02234e5) |

---

## 2. Latency Budget (Бюджет задержек в реальном времени)

| Этап конвейера | Максимальный бюджет (Target) | Критический порог | Текущее значение |
|---|---|---|---|
| CameraX -> ImageProxy буфер | $\le 10$ мс | $25$ мс | $\le 8$ мс |
| ML Kit Object Detection | $\le 30$ мс | $60$ мс | $\le 25$ мс |
| ML Kit OCR Text Recognition | $\le 35$ мс | $70$ мс | $\le 30$ мс |
| Smart Parser + Spatial Matcher | $\le 5$ мс | $15$ мс | $\le 2$ мс (JVM) |
| Суммарный такт Контура 1 | $\le 45$ мс | $80$ мс | $\approx 35-40$ мс |
| Отклик TextToSpeech (Audio Latency) | $\le 100$ мс | $250$ мс | $\le 15$ мс (диспетчеризация) |
| Deep VLM (Контур 2, по запросу) | $\le 1500$ мс | $3000$ мс | $\approx 1000-1400$ мс |

---

## 3. Активные задачи и дорожная карта (Roadmap)

- [x] **Фаза 0: Спецификация и Промпт**
  - [x] Разработка мультиагентного системного промпта (`SYSTEM_PROMPT.md`).
  - [x] Фиксация базовой архитектуры (`docs/architecture/ARCHITECTURE.md`).
  - [x] Формирование журнала решений ADR (`docs/architecture/DECISION_LOG.md`).
- [x] **Фаза 1: Семантическое ядро (TDD & Domain-First) [ЗАВЕРШЕНА]**
  - [x] Согласована архитектура: Двухшаговый алгоритм (Spatial + Regex), модуль `:core:domain`, шаблон озвучки с выгодой (ADR-005).
  - [x] Создание чистого Kotlin-модуля `:core:domain` в Gradle с Java 17 toolchain.
  - [x] Определение чистых моделей (`NormalizedRect`, `RawTextBlock`, `PriceTag`).
  - [x] Разработка набора TDD unit-тестов с реальными кейсами ценников РФ (Пятёрочка, Магнит, ВкусВилл, Лента).
  - [x] Реализация `SmartPriceParser` и успешное прохождение всех тестов (`BUILD SUCCESSFUL`).
- [x] **Фаза 2: Видеопоток и детекторы (CameraX + ML Kit) [ЗАВЕРШЕНА]**
  - [x] Настройка анализатора кадров CameraX с защитой от переполнения буфера (`STRATEGY_KEEP_ONLY_LATEST`).
  - [x] Интеграция ML Kit Object Detection (Stream Mode) + Text Recognition v2 в `GlazokImageAnalyzer`.
  - [x] Добавление разрешений камеры в `AndroidManifest.xml`.
  - [x] Пространственная привязка ценников к объектам (`DetectedItem.associatedPrice`).
  - [x] Успешная компиляция модуля `:app` (`BUILD SUCCESSFUL`).
- [x] **Фаза 3: Аудио-тактильный арбитр и доступность [ЗАВЕРШЕНА]**
  - [x] Реализация менеджера очереди `SpeechQueueArbiter` с трехуровневым приоритетом.
  - [x] Реализация механизма Debounce (5 секунд) для защиты от повторного озвучивания одного и того же ценника.
  - [x] Реализация `HapticManager` для тактильного подтверждения захвата цели (Center Lock).
  - [x] Добавлено разрешение `VIBRATE` в `AndroidManifest.xml`.
- [x] **Фаза 4: Пользовательский интерфейс (Compose HUD) [ЗАВЕРШЕНА]**
  - [x] Полноэкранный видоискатель CameraX `PreviewView` через `AndroidView`.
  - [x] Высокопроизводительный `GlazokHudOverlay` на базе Canvas (зеленые бейджи ценников, синие рамки предметов, золотое перекрестье прицела).
  - [x] Панель управления `GlazokControlBar`: переключатели зума (1x, 2x, 3x), фонарик, кнопка повтора озвучки, бейдж задержки в мс.
  - [x] Обработка runtime-разрешений камеры в `MainActivity` с понятным экраном-заглушкой.
  - [x] Полная сборка APK: `:app:assembleDebug` (`BUILD SUCCESSFUL in 1m 2s`).
- [x] **Фаза 5: Контур 2 (Deep VLM On-Demand: Gemini API / Cloud Vision) [ЗАВЕРШЕНА]**
  - [x] Сервис захвата кадра высокого разрешения `CameraManager.takeSnapshot()`.
  - [x] Реализация `GeminiVlmService` с оптимизированным сжатием и промптом для состава, бренда и срока годности.
  - [x] Интеграция жеста двойного тапа (Double-tap) и кнопки «🔍 Состав».
  - [x] Тактильный импульс `vibrateVlmTriggered()` и всплывающая информационная карточка.
  - [x] Добавлено разрешение `INTERNET` в `AndroidManifest.xml`.
  - [x] Успешная сборка и валидация (`BUILD SUCCESSFUL in 5s`).
