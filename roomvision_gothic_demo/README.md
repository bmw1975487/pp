# RoomVision Gothic — 1.0 Single World Edition

Это полноценная single-world версия Android-приложения. Ограничение версии только одно: в каталоге пока доступен один мир — **«Готический замок»**. Качество движка, интерфейса и материалов не считается «демо-урезанным».

## Пользовательский сценарий

1. Открыть приложение.
2. Нажать **«ВОЙТИ В ЗАМОК»**.
3. Разрешить камеру.
4. Камера включается сразу — без ARCore, без сканирования комнаты и без поиска плоскостей.
5. Каждый живой кадр проходит через GPU renderer и отображается в готическом стиле.
6. Кнопка затвора сохраняет уже обработанный кадр в `Pictures/RoomVision`.

## Визуальный World Pack

`app/src/main/assets/worlds/gothic_castle/`

- `stone_albedo.jpg` — реальный high-detail bitmap каменной кладки;
- `stone_detail.jpg` — микрофактура;
- `cracks.png` — отдельная карта трещин;
- `grunge.jpg` — сырость/грязь/старение;
- `fog.jpg` — карта анимированного тумана;
- `gothic_hero.jpg` — оригинальный hero-арт главного экрана;
- `world.json` — параметры мира.

Материалы генерируются детерминированно скриптом `generate_assets.py` перед CI build. В исходный ZIP после CI они уже включены как готовые файлы.

## Renderer

- Camera2 live preview;
- OpenGL ES 2.0;
- External OES camera texture;
- 5 дополнительных material texture samplers;
- edge-aware mixing для сохранения контуров реальной сцены;
- сохранение реальной яркости/теней;
- cold cinematic grade;
- damp/grunge;
- crack layer;
- animated fog;
- dual torch flicker;
- film grain;
- vignette;
- гироскопическая компенсация UV для лёгкой псевдо-привязки фактуры.

## Build stack

- JDK 17
- Gradle 8.13
- AGP 8.13.2
- compileSdk/targetSdk 36
- Build Tools 35.0.0
- minSdk 29
- Java 17

Без Unity, ARCore, сервера и сторонних runtime SDK.

## Build Gate

CI намеренно падает, если итоговый APK меньше 2 МБ. Это защита от повторной случайной сборки технической asset-less версии.
