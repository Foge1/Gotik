# GruzchikiApp 🚛

Android-приложение для управления заказами грузчиков. Два режима работы: **Диспетчер** и **Грузчик**.

## Стек технологий

- **Kotlin** + Jetpack Compose
- **Hilt** — dependency injection
- **Room** — локальная база данных
- **ViewModel** + **StateFlow** — архитектура
- **Navigation Compose** — навигация
- **DataStore** — хранение настроек

## Архитектура

Проект построен по Clean Architecture:
```
app/
├── data/        # Room DB, DAO, репозитории, маперы
├── domain/      # Use cases, модели, интерфейсы репозиториев
├── presentation/ # ViewModels
├── ui/          # Compose экраны
├── di/          # Hilt модули
└── navigation/  # NavGraph
```

## Сборка

```bash
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`

## Требования

- Android SDK 34
- minSdk 24 (Android 7.0+)
- JDK 17
