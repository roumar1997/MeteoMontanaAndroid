# KMP — Kotlin Multiplatform (Android + iOS)

> **Migración completada.** `domain/` y `data/` compartidos en Kotlin entre
> Android (Jetpack Compose) e iOS (SwiftUI), con paridad total. Este
> documento ya no rastrea fases — queda como **referencia técnica** del
> patrón que hace falta cada vez que se añade un puerto nuevo que toque una
> API nativa de iOS (Bluetooth, cámara, sensores, etc.).

## Stack

- **SKIE** (Touchlab, plugin Gradle en `shared`): expone `StateFlow` como
  `AsyncSequence`/`.value` y `suspend` como `async throws` en Swift.
- **Motor HTTP iOS**: `ktor-client-darwin`.
- **DI**: factory manual en Kotlin — `di/IosDependencyContainer` (commonMain)
  construye HttpClient + apis + repos + use cases; Swift solo instancia el
  contenedor (`AppDependencies.swift`). No hay Koin.
- **Firebase iOS**: vía SPM (no CocoaPods), declarado en `iosApp/project.yml`.
- **Proyecto Xcode**: generado con **XcodeGen** (`xcodegen generate`) a partir
  de `iosApp/project.yml` — el `.xcodeproj` no se versiona.
- **Framework compartido**: `:shared:embedAndSignAppleFrameworkForXcode`.

## El patrón bridge (Swift ↔ Kotlin `suspend`)

Swift puede **llamar** una función `suspend` de Kotlin sin problema (SKIE la
expone como `async throws`). Pero **implementar** una interfaz `suspend` de
Kotlin *desde* Swift no es directo — para eso:

1. En `iosMain`, define una interfaz de **callbacks puros** (sin
   `suspend`/`Flow`) que Swift pueda implementar, p. ej.
   `IosLocationBridge { func current(_ cb: @escaping (UserLocation?) -> Void) }`.
2. Swift implementa esa interfaz con la API nativa real (`CLLocationManager`,
   `CBCentralManager`, `FirebaseAuth`...).
3. También en `iosMain`, una clase Kotlin envuelve el bridge Swift con
   `suspendCancellableCoroutine` (una sola llamada) o `callbackFlow` +
   `awaitClose` (streaming) para cumplir la interfaz `suspend`/`Flow` real
   del puerto en `commonMain`.
4. El bridge Swift se instancia en `AppDependencies.swift` y se pasa al
   `IosDependencyContainer`.

Ejemplos ya en el repo: `IosLocationProvider`/`LocationBridge.swift`,
`IosAuthService`/`AuthBridge.swift`, `IosChatService`/`ChatBridge.swift`.

### Gotcha de SKIE: primitivos dentro de closures/función-tipo

Kotlin `Int?`/`Long?`/`Double?`/`Boolean?` (nullable) — tanto en parámetros
normales como dentro de un tipo función (closure) — llegan a Swift **boxeados**
(`KotlinInt`, `KotlinLong`...), no como el tipo nativo. Al construir/pasar
estos valores desde Swift hace falta envolver explícitamente:
`KotlinInt(int: x)`, `KotlinDouble(double: x)`, `KotlinBoolean(bool: x)`.
Los primitivos **no nulos** sí llegan nativos (`Int32`, `Double`, `Bool`).

### Consumir un `Flow` de Kotlin desde Swift

Patrón establecido en el repo: `for await item in someFlow { ... }` (sin
`try`, sin `do/catch`) — los `Flow` construidos con `callbackFlow` en este
código no lanzan en la práctica.

## Sin Mac — cómo se hizo

Toda la implementación de las pantallas iOS (tras el bridge inicial) se hizo
**sin Mac disponible**, usando GitHub Actions como único compilador: lote de
cambios → push → el resultado del CI (verde/rojo) es el único feedback real.
Instalación en iPhone para pruebas también sin Mac, vía AltStore — ver
`CLAUDE.md` sección "Probar la app iOS en el iPhone SIN MAC".
