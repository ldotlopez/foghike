# Hike Explorer

App de senderismo para Android en la que el mapa empieza cubierto de niebla, que se despeja alrededor
de los lugares por los que has caminado, como en un juego de estrategia. La niebla se revela con el
GPS en directo o importando rutas GPX. Los mapas base son ortofotos del IGN (España), descargadas
para usarlas sin conexión.

FogHike es el nombre interno (paquete `com.cuarentaydos.foghike`, nombres de clases y archivos).

## Funciones

- Mapas sin conexión: un mapa general de España incluido, más zonas dibujadas a mano y descargadas como MBTiles.
- Importación de GPX (varios archivos a la vez) para revelar rutas recorridas en el pasado.
- Modo seguimiento: un servicio en primer plano registra tu posición; un botón lo inicia y lo detiene.
- Niebla dibujada por vista con niveles de detalle; opacidad ajustable en Ajustes → Avanzado.
- Interfaz en inglés y español.

## Preajustes

En Ajustes hay dos interruptores que fijan varios ajustes a la vez. Mientras uno está activo, los
ajustes que controla se ven desactivados con el valor fijado y el texto «Fijado por …»; al apagarlo
vuelven los valores que hubieras elegido.

| Preajuste | Dónde | Qué fija |
| --- | --- | --- |
| Ahorro de batería | Ajustes → Mapa | Intervalo del GPS a 15 s; el rastro se redibuja cada 30 s |
| Dispositivo antiguo | Ajustes → Avanzado | Detalle de la niebla con el mapa alejado en «Bajo»; como mucho 1.500 piezas de niebla; el rastro se redibuja cada 30 s |

El límite de piezas de niebla y el refresco del rastro no aparecen como ajustes propios: solo los
cambian estos preajustes.

## Requisitos

- Android 12 (API 31) o superior, móvil ARM.
- JDK 21 para compilar.

## Instalación manual con adb

La app no está en Google Play: se instala el APK a mano. Con `adb` se hace desde el ordenador.

1. **Activa la depuración USB en el móvil.** En Ajustes → Información del teléfono, toca siete veces
   «Número de compilación» para activar las opciones de desarrollador. Después, en Ajustes → Sistema →
   Opciones de desarrollador, activa «Depuración USB». Los nombres de los menús cambian un poco según
   el fabricante.
2. **Instala adb en el ordenador.** Viene en las
   [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools); en
   Debian/Ubuntu también con `sudo apt install adb`.
3. **Conecta el móvil por USB** y acepta en su pantalla el aviso «¿Permitir depuración USB?». Comprueba
   que aparece como `device`:

   ```sh
   adb devices
   ```

4. **Descarga el APK** (`hike-explorer-<versión>.apk`) de la página de
   [releases de GitHub](https://github.com/ldotlopez/foghike/releases) e instálalo:

   ```sh
   adb install -r hike-explorer-v0.1.apk
   ```

   `-r` actualiza la app si ya estaba instalada, sin perder las rutas ni los mapas descargados.

   Si aún no hay ninguna release, compila el APK tú mismo (sección «Compilar») e instala
   `app/build/outputs/apk/debug/app-debug.apk` igual, o usa directamente `./gradlew installDebug`.

Sin cable (Android 11 o superior): en Opciones de desarrollador → «Depuración inalámbrica» →
«Vincular dispositivo con código de vinculación», ejecuta `adb pair <ip>:<puerto>` con el código que
muestra el móvil y después `adb connect <ip>:<puerto>` con el puerto de la pantalla principal de
depuración inalámbrica. El móvil y el ordenador deben estar en la misma red.

Errores frecuentes:

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: el APK está firmado con otra clave que el instalado (por
  ejemplo, releases firmadas con claves distintas). Hay que desinstalar primero con
  `adb uninstall com.cuarentaydos.foghike`, **lo que borra todas las rutas y mapas** del móvil.
  Las compilaciones de depuración no chocan con la release: se instalan aparte, como
  `com.cuarentaydos.foghike.debug` («Hike Explorer (debug)»), con sus propios datos.
- `INSTALL_FAILED_VERSION_DOWNGRADE`: el APK es más antiguo que el instalado.
- `no devices/emulators found` o `unauthorized`: revisa que el cable transmita datos y vuelve a
  aceptar el aviso de depuración en el móvil.

Al abrir la app por primera vez pide los permisos de ubicación y notificaciones y la exención de
batería; sin ellos, el seguimiento con la pantalla apagada puede perder puntos.

## Compilar

```sh
./gradlew assembleDebug   # compilar
./gradlew test            # tests unitarios en la JVM
./gradlew installDebug    # instalar en un dispositivo conectado
```

El mapa general incluido `app/src/main/assets/spain.mbtiles` (zoom 5–9) ya está en el repositorio.
Para regenerarlo, ejecuta `python3 tools/build_spain_mbtiles.py` desde la raíz del repositorio.

## Publicación

Al subir una etiqueta (tag) se compila un APK de release ARM firmado y se publica como release de
GitHub (`.github/workflows/release.yml`). La clave de firma nunca está en el repositorio; se pasa
con las variables de entorno `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` y
`SIGNING_KEY_PASSWORD`. Todas las releases deben usar la misma clave.

## Documentación

- [`FogHike — Documento de diseño.md`](FogHike%20—%20Documento%20de%20diseño.md) — documento de diseño, fuente de verdad de las decisiones de producto.
- [`docs/`](docs/) — documentación técnica (en inglés): disparadores de recálculo, valores
  configurables, registro de decisiones, trabajo planificado y pruebas manuales en dispositivo.

## Atribución

Ortofotos: PNOA cedido por © Instituto Geográfico Nacional de España.
