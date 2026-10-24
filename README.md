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

## Requisitos

- Android 12 (API 31) o superior, móvil ARM.
- JDK 21 para compilar.

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
