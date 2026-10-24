# Hike Explorer — Documento de diseño

Oct 23, 2026 · actualizado Oct 24, 2026 · @Luis

## Visión y concepto

Hike Explorer (nombre interno FogHike) es una app Android de senderismo en la que el mapa empieza cubierto por una niebla opaca que se despeja alrededor de los lugares por los que el usuario camina, igual que en los juegos de estrategia.

La niebla se revela a partir de la posición GPS en vivo o de rutas ya recorridas: el usuario puede importar ficheros GPX (y, más adelante, el JSON que exporta la Cronología de Google Maps desde el móvil) para revelar el mapa de forma retroactiva. Los mapas base son vistas satélite del Instituto Geográfico Nacional (IGN) de España, descargadas para usarse sin conexión.

## Alcance del PoC

El PoC cubre tres funciones de extremo a extremo: descargar mapas offline, importar rutas GPX y registrar los movimientos del usuario en vivo. Sobre los mapas descargados se muestra la niebla, con opacidad ajustable para poder depurar.

**Incluido**

- Descarga de mapas offline: mapa general de España, selección de una o varias áreas dibujadas a mano alzada y botón de descarga de la vista satélite del IGN.
- Importación de rutas GPX para añadirlas a las áreas exploradas.
- Modo seguimiento: activa el GPS y registra los movimientos; un único botón alterna entre iniciar y parar la exploración.
- Mapas descargados y niebla en la pantalla principal: los mapas offline se ven bajo una niebla cuya opacidad se ajusta con un deslizador, pensada para depurar y comprobar que los mapas cargan.

**Fuera del PoC**

- Importación del JSON de Google Maps.
- Pantalla de ajustes con reprocesamiento.
- Versión iOS.

También está diseñado, pero sin implementar, el intercambio de áreas exploradas entre usuarios por NFC (ver «Intercambio de áreas por NFC»).

## Arquitectura y stack

El stack elegido es Kotlin Multiplatform con MapLibre nativo, priorizando tener un PoC funcionando rápido sobre la pureza arquitectónica desde el primer día.

- **Multiplataforma:** hoy el código es Kotlin para Android, organizado para pasar a Kotlin Multiplatform sin reescribirlo. Se pensó el stack con una futura versión iOS en mente y se compararon Kotlin Multiplatform, Flutter y React Native. Flutter y React Native se descartaron por peor rendimiento en overlays personalizados sobre el mapa.
- **Mapa:** MapLibre, que tiene SDK nativo en Android e iOS.
- **Interfaz:** Jetpack Compose con Material 3 y una paleta fija de marca generada a partir del verde del icono (`#3DDC84`), en claro y oscuro. Las reglas de diseño de cada pantalla están en `docs/ui/`. El mapa sigue siendo la vista nativa de MapLibre, que Compose coloca bajo los paneles con `AndroidView`; cada pantalla es una Activity sin librería de navegación ni ViewModel. Al portar a iOS se puede pasar a Compose Multiplatform y compartir las pantallas, o hacerlas en SwiftUI.
- **Un solo módulo:** hoy existe únicamente el módulo Android `app`. La lógica pura (`geo/` y `data/`) se extraerá a un módulo `shared` de KMP cuando haga falta portar a iOS, no antes.
- **Persistencia:** Room (2.7 o superior, con KSP). Desde la 2.7 Room también funciona en iOS dentro de Kotlin Multiplatform con su driver SQLite incluido, así que al portar no hace falta cambiar a SQLDelight: basta con que `data/` se escriba desde hoy de forma compatible (ver «Estructura y convenciones»).
- **Versiones de Android:** mínimo Android 12 (API 31), porque algunos usuarios usan móviles antiguos como GPS de senderismo; `targetSdk` y `compileSdk` en la última versión estable. Ver «Compatibilidad con Android 12».
- **Ubicación:** Fused Location Provider de Play Services, con coroutines para el trabajo asíncrono.
- **Mapas base:** vista satélite del IGN en formato MBTiles, para uso sin conexión.

## Mecánica de revelado

Cada punto GPS revela todas las celdas de una rejilla cuyo centro cae dentro de un radio configurable, no una sola celda. Dos parámetros lo gobiernan: el tamaño de celda (`cellSizeMeters`) y el radio de revelado (`revealRadiusMeters`). Por defecto valen 25 m y 30 m, y se cambian en Ajustes > Avanzado.

1. El punto llega como posición (latitud, longitud y marca de tiempo opcional), venga del GPS en vivo o de un fichero importado.
2. Se cuantiza a su celda de la rejilla según el tamaño de celda.
3. Se calculan las celdas candidatas cercanas y se filtran por distancia Haversine real: solo quedan las que tienen el centro dentro del radio.
4. Las celdas resultantes se guardan como reveladas, ignorando las que ya existían.

El identificador de cada celda incluye fila, columna y el tamaño de celda con el que se calculó. Así, si el usuario cambia el tamaño, los identificadores nuevos no chocan con los antiguos aunque quedara alguna celda vieja; el radio no forma parte del identificador porque solo decide qué celdas se revelan.

**Geometría de la celda.** La altura de las celdas es constante y su anchura depende de la latitud de su fila, no de la del punto GPS. Así, el identificador de una celda basta para saber qué rectángulo cubre, y eso permite dibujar la niebla a partir de los identificadores guardados.

**Reprocesamiento.** Tanto el radio como el tamaño de celda son configurables desde la app, aunque eso implique recalcular datos ya guardados. Al cambiar cualquiera de los dos se borran todas las celdas reveladas y se recalculan desde los puntos GPS crudos. Por eso se almacenan los puntos de cada ruta y no solo las celdas resultantes.

Los dos valores se eligen de una lista cerrada, para acotar el coste de la niebla y evitar configuraciones absurdas: celda de 10, 25, 50 o 100 m, y radio de 1,2×, 2× o 3× la celda (por defecto, 25 m y 1,2×, es decir, 30 m). El reprocesamiento se lanza una sola vez al salir de Ajustes, aunque hayan cambiado los dos valores, y muestra su progreso; mientras tanto, bajo cada valor cambiado una línea pequeña avisa de que se recalculará al cerrar la pantalla. Si la app muere a mitad, se repite en el siguiente arranque. También reconstruye los niveles de detalle.

**Niveles de detalle (LOD).** Para dibujar la niebla a zoom bajo sin miles de rectángulos diminutos, además de la rejilla fina se mantienen rejillas más gruesas, cada una 8 veces mayor que la anterior: con la celda por defecto, 200 m, 1,6 km y 12,8 km. Usan el mismo esquema de geometría (celda de `cellSizeMeters × 8ⁿ`), así que su identificador también basta para saber qué rectángulo cubren. Una celda gruesa está revelada si contiene el centro de alguna celda fina revelada, y guarda en `revealedCount` cuántas contiene (las finas guardan 1); cómo se dibuja según ese número se explica en «Niebla a zoom bajo». Se calculan al guardar cada celda fina nueva (unas pocas inserciones y actualizaciones más; solo las finas nuevas suman al contador) y se guardan con su origen en la misma tabla `revealed_cell`: como el identificador ya incluye el tamaño, no hace falta otra tabla, y el resaltado de importadas también funcionará a zoom bajo.

## Modelo de datos

La base de datos local guarda cuatro tablas implementadas; otra está diseñada pero aún no existe.

| Tabla | Clave | Campos | Para qué sirve | Estado |
| --- | --- | --- | --- | --- |
| `revealed_cell` | `cellId` (`tamaño:fila:columna`) | `cellSize`, `gridRow`, `gridCol`, `firstRevealedAt`, `source` (`live`, `gpx_import`, `gmaps_import`), `revealedCount`; índice por tamaño, fila y columna | Celdas reveladas, de la rejilla base y de los niveles de detalle, y su origen | Implementada |
| `track_point` | `trackId` + `sequenceIndex` | `lat`, `lon`, `timestamp` (opcional), `accuracy` (metros, opcional), `source` | Puntos GPS crudos, necesarios para reprocesar (el origen permite procesar primero los propios). El `trackId` de un GPX es su huella de coordenadas | Implementada |
| `downloaded_area` | `id` autogenerado | nombre, límites (lat/lon mínimas y máximas), zoom mínimo y máximo, nombre del fichero `.mbtiles`, fecha de descarga | Registro de las áreas de mapa descargadas | Implementada |
| `AppSettings` | — | `revealRadiusMeters`, `cellSizeMeters`, `trailMinutes`, `userAlias` | Ajustes configurables por el usuario; el alias se envía con las rutas compartidas | Sustituida: los ajustes se guardan en `SharedPreferences` (decisión 0011); el alias, cuando llegue el intercambio |
| `track` | `trackId` | `source`, `createdAt` (inicio de la grabación o momento de importar), `name` (opcional), `startedAt` (hora del primer punto, opcional). Con el intercambio: `fingerprint` y `senderAlias` (solo `nfc_import`) | Describir cada ruta para listarla y borrarla; con el intercambio, evitar importarla dos veces y saber de quién viene | Implementada (versión 3), sin las columnas del intercambio |

Mientras nadie más que el autor use la app, Room usa migración destructiva: si cambia el esquema, se borran los datos locales. Desde la primera versión que se reparta a usuarios de prueba, cada cambio de esquema lleva su migración real, para no borrar las rutas de los probadores.

Con el intercambio por NFC, el campo `source` de `revealed_cell` admitirá un cuarto valor, `nfc_import`, para las celdas derivadas de rutas recibidas. La tabla `track` crecerá para guardar el identificador, el origen, una huella de cada ruta para no importar dos veces la misma y el alias de quien la envió (ver «Intercambio de áreas por NFC»).

## Mapas offline

El usuario elige en un mapa general de España qué zonas quiere llevar sin conexión; cada zona se descarga como un fichero MBTiles independiente con la vista satélite del IGN.

1. Desde la pantalla principal se abre la descarga de mapas, centrada en la posición del usuario a zoom 11 (o en España entera si no hay ubicación), sobre el mapa general incluido en la app.
2. El usuario rodea con un dedo cada zona que quiere descargar, y cada trazo se cierra como un polígono. Se pueden dibujar varias. Con dos dedos se mueve y amplía el mapa: en cuanto se apoya el segundo dedo se descarta el trazo empezado y el gesto pasa al mapa. No hay botón para cambiar de modo; a cambio, un dedo no desplaza el mapa ni el doble toque lo amplía.
3. El usuario fija el rango de zoom (13 a 16 por defecto) y pulsa el botón de descarga.
4. Por cada polígono se calculan solo los tiles que lo tocan (esquina dentro, vértice dentro o borde que cruza), no todos los de su rectángulo envolvente, en el esquema Web Mercator estándar y se piden al servicio WMTS de ortofotos del IGN (capa `OI.OrthoimageCoverage`, matriz `GoogleMapsCompatible`, JPEG, hasta zoom 19).
5. Los tiles se escriben en un `.mbtiles` conforme al estándar y se registra el área en la base de datos.

La descarga corre en WorkManager (el planificador de tareas de Android), no en la pantalla: sigue aunque el usuario salga, espera a tener conexión y se reinicia si Android mata el proceso. Las áreas se descargan de una en una, en orden, con una notificación de progreso que tiene un botón Cancelar (cancela también las que esperan detrás). El fichero se escribe como `.part` y solo se renombra y registra al terminar, así que una descarga cancelada o fallida no deja nada en disco. La pantalla de descarga muestra el progreso al volver a ella. Si el proceso muere a mitad, el área vuelve a empezar desde cero.

Notas de diseño:

- **Mapa general de España.** La app incluye un `.mbtiles` con la vista satélite del IGN de toda España a zoom 5–9 (4,8 MB, 551 tiles). Se genera una vez con `tools/build_spain_mbtiles.py` (Python, misma fuente WMTS y mismo formato que la descarga de la app) y va en los assets del APK; en el primer arranque se copia al almacenamiento de la app, porque SQLite necesita una ruta de fichero, y lo sirve el mismo servidor local que los demás mapas. Se usa como fondo en las dos pantallas; en la principal queda bajo la niebla como cualquier otro mapa (ver «Mapa principal y niebla»).
- Para elegir áreas con precisión, la pantalla de descarga pone encima la capa online del IGN en los zooms superiores al 9. Sin conexión se puede ver el mapa general, pero no descargar.
- La descarga es secuencial y sin reintentos: un tile que falla se omite y se registra en el log.
- El uso del servicio es gratuito, pero exige mostrar la atribución "PNOA cedido por © Instituto Geográfico Nacional de España" donde se muestren las imágenes.

## Mapa principal y niebla

La pantalla principal muestra los mapas bajo una niebla que cubre toda la pantalla salvo las celdas reveladas, sea España, el mar o los Pirineos, y un deslizador ajusta su opacidad para comprobar que los mapas cargan.

**Composición por capas**, de abajo arriba:

| # | Capa | Contenido | Coste |
| --- | --- | --- | --- |
| 1 | Fondo | Color oscuro | Nulo |
| 2 | Mapa general | MBTiles de España, zoom 5–9; por encima se amplía el zoom 9 | Tiles |
| 3 | Áreas descargadas | Un MBTiles por área, zoom 13–16 por defecto | Tiles |
| 4 | Niebla | Toda la ventana visible menos las celdas reveladas, con la opacidad del deslizador | Calculada solo para lo visible |
| 5 | Resaltado | Filtro de color sobre las celdas importadas, activable | Igual que la niebla |
| 6 | Rastro | Línea naranja por donde ha pasado el usuario en la última hora, que se difumina con la antigüedad | Unos cientos de segmentos; se redibuja cada 10 s con la pantalla encendida |
| 7 | Posición | Punto con flecha hacia donde mira el móvil (brújula), del componente de ubicación de MapLibre | GPS y brújula solo con la pantalla encendida |

- **Mapas descargados.** Cada área descargada es una capa de imagen sobre el mapa general. La versión de MapLibre del proyecto no lee MBTiles directamente, así que la app levanta un pequeño servidor HTTP local, accesible solo desde el propio teléfono (127.0.0.1), que sirve los tiles de los ficheros `.mbtiles`. El HTTP sin cifrar se permite únicamente para esa dirección. Se mantiene así en el PoC: funciona, y para una app de prueba con menos de 100 usuarios migrar a PMTiles no compensa (exigiría actualizar MapLibre y cambiar el formato de descarga). Se reconsiderará solo si el servidor da problemas en dispositivos reales.
- **Niebla.** Se calcula solo para la ventana visible más un margen de una pantalla por cada lado, nunca para España entera, así que su coste depende del tamaño de la pantalla y no de cuánto se haya explorado. Las celdas sin revelar de cada fila se agrupan en tramos, y las filas consecutivas sin nada revelado se funden en un solo rectángulo; cada fila cuesta unos pocos rectángulos y no un polígono por celda.
- **Nivel de detalle según el zoom.** Se usa el nivel más fino cuyas celdas miden al menos 4 px en pantalla; con la celda de 25 m a latitudes de España sale la fina a zoom 15 o más, la de ×8 entre 12 y 14, la de ×64 entre 9 y 11 y la de ×512 a 8 o menos (ver «Niveles de detalle» en «Mecánica de revelado»). Con celdas de entre 4 y 30 px y 3×3 pantallas salen a lo sumo unas 1.800 filas, y como las rutas son estrechas, unos pocos miles de rectángulos como máximo (límite: 5.000). Si aun así se supera el límite de rectángulos, se pasa al nivel más grueso siguiente en vez de no dibujar.
- **Niebla a zoom bajo.** Ver la sección siguiente. El modo se elige en Ajustes > Avanzado («Niebla con el mapa alejado»).
- **Cuándo se recalcula.** Al pararse la cámara (`onCameraIdle`) si ha salido del margen calculado o ha cambiado de nivel de detalle, y cuando cambian las celdas. Mientras el usuario arrastra no se calcula nada: MapLibre ya tiene los rectángulos en la GPU. El cálculo va fuera del hilo principal. Con la pantalla apagada no se calcula la niebla; el servicio de seguimiento solo guarda puntos y celdas.
- **Aspecto.** La niebla es estática para no gastar batería: una textura de ruido fractal (256 px, de gris azulado a blanco, con zonas algo más finas) generada una sola vez al cargar el estilo y repetida por MapLibre como patrón de relleno (`fill-pattern`), así que dibujarla no cuesta más que un color plano. La niebla animada queda en `docs/PLAN.md`.
- **Rastro reciente.** Se dibujan los puntos guardados de la última hora (cualquier origen con hora), unidos por ruta, con opacidad 1 en el último punto que baja hasta 0 a la hora. Los puntos imprecisos se ignoran, como en el revelado. La duración (una hora por defecto), el color, el grosor y si se muestra se eligen en Ajustes > Avanzado. Se consulta cada 10 s (30 s con «Teléfono lento» o «Ahorro de batería») mientras la pantalla está encendida, así recoge los puntos nuevos y difumina los viejos.
- **Posición y orientación.** El componente de ubicación de MapLibre en modo brújula: punto con flecha hacia donde apunta el móvil, sin mover la cámara. Sigue el ciclo de vida de la pantalla, así que con la pantalla apagada no usa GPS ni brújula; el registro de puntos lo sigue haciendo el servicio. Aparece gris cuando no hay una posición reciente.
- **Opacidad ajustable.** Un deslizador de 0 % a 100 % (70 % por defecto) controla la niebla: con 0 % se ven los mapas completos y con 100 % quedan tapados. El valor se recuerda entre sesiones.
- **Actualización.** La niebla se recalcula al registrar un punto GPS, al importar rutas (GPX, Google Maps o intercambio) y al reprocesar. Los mapas se recargan al volver de la pantalla de descarga y la cámara se encuadra en el área más reciente, nunca por debajo de su zoom mínimo para que se vea.
- **Posición inicial.** Al abrir la app el mapa se centra en el usuario al zoom elegido en Ajustes (14 por defecto) y en el modo elegido allí (libre, seguir o brújula): primero en la última posición conocida, que es instantánea, y luego en una nueva. El permiso de ubicación se pide una vez en el primer arranque; sin él, el mapa se encuadra en el área más reciente o en España.
- **Zoom mínimo 5.** Por debajo no hay mapa general que dibujar, así que no se permite alejarse más.
- **Estado visible.** El texto superior indica las celdas reveladas (propias y total, cuando haya importadas) y los mapas offline cargados, y avisa si no hay mapas descargados.

### Niebla a zoom bajo

Al alejar el zoom, la niebla pasa a celdas gruesas, y con la regla «revelada si contiene alguna celda fina» una ruta de 60 m de ancho despeja franjas de 200 m, 1,6 km o 12,8 km. En pantalla el exceso es como mucho una celda (≤ 30 px), pero enseña terreno sin explorar. Las rejillas gruesas no encajan columna a columna con las finas (el ancho depende de la latitud de cada fila), así que el contador se calcula por el centro de cada celda fina y no con un `GROUP BY` en SQL.

Una ruta recta que cruza una celda gruesa cubre, aproximadamente: ~30 % de una de 200 m (19 de 64 celdas finas), ~4 % de una de 1,6 km (150 de 4.096) y ~0,5 % de una de 12,8 km (1.200 de 262.144). Por eso un umbral único de porcentaje deja la vista de España entera bajo niebla.

Estrategias, para probarlas en el dispositivo. Las que dependen de `revealedCount` se intercambian con `FogMode` sin tocar los datos:

| Estrategia | Idea | Coste | Estado |
| --- | --- | --- | --- |
| `ANY_CELL` | Celda gruesa despejada si contiene alguna fina revelada | — | Implementada (comportamiento original) |
| `DENSITY` | Celda gruesa revelada con niebla parcial: `1 − ln(1+n)/ln(1+capacidad)`; escala logarítmica para que una ruta en una celda de 12,8 km se vea (~0,45) | Contador + una propiedad por elemento y `fill-opacity` por expresión | **Implementada, activa** |
| Umbral | Despejada solo si `revealedCount ≥ X` (X por nivel; un X único en porcentaje deja España con niebla) | Un filtro más sobre el mismo contador | Pendiente |
| Umbral + densidad | Por encima del umbral, despejada; por debajo, niebla parcial | Combinar las dos anteriores | Pendiente |
| Menos píxeles por celda | Bajar el mínimo de 4 px de `lodLevelFor` a 1–2 px: el nivel fino aguanta un tramo más de zoom y el exceso se reduce a la mitad o menos | Una constante; más rectángulos, así que el límite de 5.000 saltará antes de nivel | Pendiente; independiente del modo |
| Trazo a zoom bajo | Niebla gruesa sin despejar y una línea simplificada de las rutas (grosor mínimo 2 px) encima | Capa de líneas nueva y simplificación por zoom | Pendiente; deja de ser «niebla que se despeja» |
| Umbral en cascada | Despejada si lo están al menos k de sus hijas del nivel anterior | Contar hijas | Descartada: las columnas no encajan y el resultado depende de si la ruta va recta o en diagonal |

Las celdas gruesas guardadas antes de la versión 2 de la base de datos empiezan con `revealedCount = 1` (migración automática), así que en `DENSITY` se ven casi cubiertas hasta que se reprocese (paso 4 de la hoja de ruta) o se vuelva a pasar por ellas.

## Importación de rutas

Importar una ruta consiste en pasar sus puntos por el mismo cálculo de revelado que usa el seguimiento en vivo, de modo que las caminatas pasadas despejan el mapa igual que las nuevas.

**GPX (implementado).** El usuario elige uno o varios ficheros desde el selector del sistema. De él se leen los puntos de la pista (latitud, longitud y hora) y se ignora todo lo demás: rutas, waypoints y extensiones. Los puntos se guardan como puntos crudos para poder reprocesarlos, y las celdas reveladas quedan marcadas con el origen `gpx_import`. El identificador de la ruta es `gpx-` más una huella de su secuencia de coordenadas (sin horas, redondeadas a 1e-6°, la misma idea que el intercambio): si ya existe, el fichero se omite y el aviso lo cuenta como ya importado. Un fichero sin puntos de pista cuenta como ilegible. La ruta se llama como el `<name>` de su pista (o de `<metadata>`) y, si no tiene, como el fichero; se importa desde la pantalla de rutas.

**Borrar rutas (implementado).** Desde la pantalla de rutas se borra una o varias, siempre tras confirmar. Como las celdas no saben qué ruta las reveló, se borran la ruta y sus puntos y se reconstruyen todas las celdas con los puntos que quedan, primero las propias (decisión 0010). La ruta que se está grabando no se puede borrar.

**JSON de Google Maps (pendiente).** El formato de referencia es el que exporta el móvil (Ajustes > Ubicación > Cronología > Exportar), porque la Cronología ya se guarda en el dispositivo y Takeout no la incluye para la mayoría de usuarios. El formato antiguo de Takeout (`Records.json`, Semantic Location History) no se soporta. Antes de escribir el parser hay que conseguir una muestra real para fijar los campos: lo esperable es leer los puntos de `timelinePath` dentro de `semanticSegments`. El parser alimentará el mismo cálculo de revelado y marcará sus celdas con el origen `gmaps_import`.

## Modo seguimiento

El seguimiento solo se activa cuando el usuario lo pide: un único botón inicia la exploración y, mientras está activa, pasa a ofrecer pararla.

- Al iniciar se encadenan, saltando los ya resueltos: permiso de ubicación precisa (y de notificaciones en Android 13+); una explicación y la petición de «Permitir todo el tiempo», solo la primera vez; la exención de la optimización de batería, en cada inicio mientras falte; y la comprobación de que la ubicación del sistema está activada, con el diálogo de Play Services para activarla. Si falta la ubicación precisa o la del sistema, no se inicia y se explica por qué.
- Después se abre una sesión nueva y se activa el GPS con alta precisión y una actualización cada 5 segundos.
- El registro corre en un servicio en primer plano de tipo ubicación, con una notificación persistente mientras dura la exploración, así que sigue registrando con la pantalla apagada o la app en segundo plano.
- **No perder datos.** Cada lote de posiciones se guarda en la base de datos en cuanto llega. Si Android mata el servicio, lo reinicia solo (`START_STICKY`) y la exploración continúa en una sesión nueva; probado en el S10e con un fallo forzado: reinicio en 1 s y un hueco de un punto. Para que ese reinicio desde segundo plano esté permitido y tenga GPS hacen falta la exención de batería y «Permitir todo el tiempo»; sin ellas el servicio se detiene limpiamente en vez de fallar. Parar desde el botón es definitivo.
- Se exige ubicación precisa: desde Android 12 el usuario puede conceder solo la aproximada, que con celdas de 25 m no sirve. Si la concede, la app lo explica y no inicia la exploración.
- Cada posición recibida se guarda como punto crudo de la sesión, con su precisión, y revela las celdas de su radio con el origen `live`. Una posición cuya precisión es peor que el radio de revelado se guarda (existe y puede servir más adelante), pero se ignora para todo lo que ve el usuario: no revela celdas, ni ahora ni al reprocesar.
- **Ubicación desactivada a mitad de exploración.** El servicio sigue activo y la notificación pasa a «Ubicación desactivada: actívala para seguir explorando»; al volver a activarla sigue registrando en la misma sesión.
- La pantalla principal muestra un contador de celdas reveladas que se actualiza con cada punto.
- Al parar se detienen las actualizaciones de ubicación y se cierra la sesión.

## Intercambio de áreas por NFC

Dos usuarios pueden fusionar sus áreas exploradas acercando los teléfonos, y la dirección del intercambio se elige en el momento del contacto. Esta función está diseñada pero no implementada.

**Requisitos**

- Los usuarios integran las áreas exploradas por otros usuarios mediante NFC.
- La dirección se configura al hacer el contacto: en un sentido, en el otro o bidireccional.
- Las áreas importadas quedan marcadas en la base de datos, de modo que se puedan mostrar solo las realmente exploradas o todas.
- Lo que se transfiere es un conjunto de ficheros GPX, no celdas, lo que elimina el problema de los distintos tamaños de celda.

**Diseño**

**Dirección.** Quien inicia el contacto elige la dirección y el otro usuario la confirma en su pantalla antes de que se transfiera nada.

| Elección del iniciador | Quién envía | Resultado |
| --- | --- | --- |
| Yo envío | El iniciador | El otro usuario recibe mis áreas |
| Yo recibo | El otro usuario | Recibo las áreas del otro |
| Intercambiar | Ambos | Cada uno recibe las áreas del otro |

**Qué se comparte.** Un conjunto de ficheros GPX con las rutas propias del emisor (orígenes `live`, `gpx_import` y `gmaps_import`), no las celdas. Cada receptor recalcula las celdas con su propio tamaño de celda y radio, así que la diferencia entre configuraciones deja de ser un problema. Las rutas recibidas de terceros no se reenvían, para no difundir recorridos ajenos.

**Selección de rutas.** El emisor elige qué envía con dos opciones: «Todas» (por defecto) o «Seleccionar», que abre la lista de rutas propias para marcarlas una a una.

**Marcado.** Las rutas recibidas se guardan con origen `nfc_import` en la tabla `Track`, con un identificador, una huella del contenido y el alias del emisor. Se envía todo lo seleccionado y el receptor descarta los GPX cuya huella ya tiene; no hay negociación previa de huellas. La huella se calcula sobre la secuencia de coordenadas, sin marcas de tiempo, para que coincida aunque las horas se hayan quitado. Si la misma ruta llega de dos emisores, se conserva el alias del primero. Las celdas derivadas de ellas también se marcan `nfc_import`. Si una celda tiene dos orígenes prevalece el propio: basta reprocesar primero las rutas propias y después las importadas, ignorando los duplicados. Fuera del reprocesamiento, en cambio, una ruta propia nueva (seguimiento en vivo o importación de GPX o Google Maps) que pase por una celda ya importada debe convertirla en explorada, y eso exige cambiar la inserción actual, que ignora los duplicados.

**Vista.** El mapa muestra siempre todas las áreas, propias e importadas, sin tener que ir a la configuración. Un botón en la pantalla principal activa o desactiva un resaltado sutil de las celdas importadas: una segunda capa de «niebla» solo sobre las celdas `nfc_import`, que deja ver el mapa pero las distingue de las exploradas por el usuario. En el PoC esa capa es un filtro de color: rectángulos semitransparentes de un tono distinto (por ejemplo, azulado al 20–30 %), dibujados con los mismos tramos por fila que la niebla, así que el coste es casi nulo. Un desenfoque o una desaturación se verían mejor, pero MapLibre no permite aplicarlos a una zona del mapa: exigirían una capa con shader propio o una copia procesada de los tiles, y quedan como mejora posterior. El estado del botón se recuerda entre sesiones, como la opacidad. El contador muestra las dos cifras: celdas propias y total. Así se cumple el requisito de distinguir «solo las realmente exploradas o todas» sin dos vistas separadas.

**Reprocesamiento.** Como las rutas recibidas se guardan como puntos crudos, el reprocesamiento las trata igual que las propias: se borran las celdas y se recalculan desde los puntos, primero los propios y luego los importados. Ya no hace falta conservar aparte celdas `nfc_import`.

**Canal de transferencia.** NFC sirve para el contacto, la identificación, el acuerdo de dirección y el consentimiento. Un conjunto de GPX es demasiado voluminoso para NFC, así que los ficheros viajan por Nearby Connections de Play Services (Bluetooth y Wi-Fi), que encaja porque Play Services ya es dependencia del proyecto. Android Beam, la API antigua de intercambio por NFC, está obsoleta y retirada en versiones recientes de Android, así que el contacto NFC usa emulación de tarjeta (HCE) en un teléfono y lectura en el otro. Nearby Connections necesita los permisos de Bluetooth `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` y `BLUETOOTH_CONNECT` (Android 12 o superior) y `NEARBY_WIFI_DEVICES` en Android 13 o superior; en Android 12 pide además la ubicación precisa, que la app ya tiene. Algunos móviles antiguos no tienen NFC: en ellos el intercambio no está disponible.

**Privacidad.** Una ruta revela más que unas celdas: trayectos exactos y horas. Por eso el intercambio requiere confirmación explícita en ambos teléfonos, el emisor elige qué rutas envía y se quitan las marcas de tiempo antes de enviarlas. Con las rutas viaja el alias del emisor (`userAlias`), que el receptor guarda; el emisor ve su alias en la pantalla de confirmación antes de enviar. Si el alias está vacío, esa pantalla lo pide antes del primer envío; después se cambia en Ajustes. Una vez entregada, una ruta no se puede retirar del teléfono del receptor.

**Validación en el receptor.** Los ficheros recibidos son datos no confiables: se limita su tamaño y se comprueba que las coordenadas sean válidas antes de importarlos.

**Riesgo pendiente**

- En iOS el NFC entre teléfonos está muy limitado y Nearby Connections no tiene SDK estable. El intercambio se diseña solo para Android; antes de prometerlo entre plataformas hay que verificar ambas piezas.

## Pantallas y flujos

El PoC tiene cuatro pantallas propias y usa el selector de ficheros del sistema para importar rutas.

| Pantalla | Qué muestra | Acciones |
| --- | --- | --- |
| Principal | Mapas descargados bajo la niebla, etiqueta con celdas reveladas y mapas, posición y rastro | Iniciar o parar la exploración (se pliega la barra); abrir la descarga de mapas; abrir las rutas; centrar y seguir la posición, con brújula (mantener pulsado elige el modo); ayuda `(?)`. Detalle en `docs/ui/screens/main.md` |
| Descarga de mapas | Mapa centrado en el usuario con las áreas ya descargadas, recuento de tiles frente al límite, progreso de la descarga y ayuda `(?)` (se abre sola la primera vez) | Dibujar áreas a mano alzada / mover el mapa; deshacer la última área o quitarlas todas; elegir el rango de zoom; recentrar; descargar. Detalle en `docs/ui/screens/download.md` |
| Rutas | Grabaciones e importaciones, de la más reciente a la más antigua: nombre (las importadas con la fecha de la ruta) y, en letra menor, fecha de alta y origen | Importar GPX; borrar una (papelera o deslizar a la izquierda) o varias (selección múltiple), siempre tras confirmar. Detalle en `docs/ui/screens/tracks.md` |
| Selector de ficheros | Selector del sistema | Elegir uno o varios GPX a importar |
| Ajustes | Mapa: modo al abrir el mapa y «Ahorro de batería». Avanzado: opacidad de la niebla con vista previa, tamaño de celda, radio de revelado, modo de niebla, «Teléfono lento», detalle de la niebla, intervalo del GPS, zoom al centrar y rastro (si se muestra; duración, color y grosor). Versión. Pendiente: alias | Los cambios se guardan al momento, salvo celda y radio, que se aplican al salir con un único reprocesamiento. Los dos interruptores agrupan varios valores y ocultan los que fijan. Detalle en `docs/ui/screens/settings.md` |

El intercambio por NFC añadiría una pantalla de contacto y confirmación (dirección, «Todas / Seleccionar» y alias propio) y un botón en la pantalla principal para resaltar las celdas importadas (ver «Intercambio de áreas por NFC»).

## Estructura y convenciones

El proyecto es un único módulo Android con el identificador de aplicación `com.cuarentaydos.foghike`, organizado en paquetes por responsabilidad:

| Paquete | Contenido | ¿Pasa a `shared`? |
| --- | --- | --- |
| `geo/` | Rejilla, revelado, niveles de detalle, rectángulos de niebla, cálculo de tiles y polígonos de descarga | Sí |
| `data/` | Entidades, DAOs y base de datos de Room | Sí |
| `core/` | Lógica de la app que usan pantallas y servicios: guardar puntos y revelar celdas (`Exploration.kt`, común a seguimiento e importación), importar una ruta sin duplicarla, calcular la niebla de una zona (`FogCells.kt`) y los tramos del rastro (`Trail.kt`) | Sí |
| raíz | `FogHikeApp` (crea la base de datos y el servidor de tiles) | No |
| `tiles/` | Servidor local de tiles y descarga de áreas a MBTiles | No |
| `gpx/` | Lector de GPX | No |
| `tracking/` | Servicio en primer plano del seguimiento | No |
| `ui/` | Las dos pantallas, el estilo del mapa y el dibujo de la niebla y el rastro en MapLibre | No |

Fuera de la app, `tools/build_spain_mbtiles.py` genera el mapa general de España.

Convenciones de desarrollo:

- Todo el código va en inglés (nombres, comentarios y commits), aunque la conversación sea en español.
- Los textos de la interfaz van en `strings.xml` en inglés y se traducen al castellano en `values-es`; Android elige según el idioma del teléfono.
- La complejidad se mantiene al nivel de los scripts y CLIs de Python bien estructurados del autor: sin arquitecturas elaboradas ni capas innecesarias, porque no tiene experiencia previa en desarrollo móvil.
- La lógica pura (`geo/`) no depende de Android ni de ninguna librería, para poder extraerla a un módulo compartido al portar a iOS.
- `data/` contiene solo entidades, DAOs y la base de datos de Room, escritos para que Room KMP los compile también para iOS: DAOs con `suspend` y `Flow` (sin `LiveData`, `Cursor` ni `Context`), fechas como `Long` en milisegundos (sin `java.time` ni otras APIs `java.*`). La creación de la base de datos, que necesita `Context` y una ruta, vive en un único fichero de Android; al portar se convierte en un `expect/actual`.
- `core/` reúne la lógica que no es geometría pura pero tampoco depende de la plataforma: usa `geo/`, `data/` y corrutinas, sin Android, MapLibre ni APIs `java.*`. Hoy tiene dos excepciones anotadas en el código, que se cambian al portar: `withTransaction` (solo Android) y `System.currentTimeMillis`.
- Pantallas, servicios y workers son pegamento con la plataforma: piden permisos, leen ficheros, dibujan en MapLibre y muestran resultados. Cualquier cálculo o decisión que también necesitaría iOS va a `core/`, `geo/` o `data/`. Así, al portar, solo se reescribe ese pegamento.
- El servidor de tiles y la lectura de MBTiles son código de Android y no se comparten: en iOS el mapa se alimentaría de otra forma, por ejemplo con PMTiles.
- No se crean el módulo `shared` ni interfaces de repositorio hasta que exista la versión iOS: los DAOs ya hacen de interfaz.

**Distribución.** La app se reparte a los usuarios de prueba (menos de 100) como APK firmado, instalado a mano. Todas las versiones deben firmarse con la misma clave, guardada fuera del repositorio y con copia de seguridad: si se pierde, los probadores tendrán que desinstalar, y perderían sus datos. Cada tag que se sube a GitHub genera una release con el APK firmado, solo para ARM; la clave va en los secretos del repositorio. Los teléfonos necesitan Google Play Services, del que dependen la ubicación y Nearby Connections.

## Compatibilidad con Android 12

La app funciona desde Android 12 (API 31, `minSdk = 31`), porque algunos usuarios llevan un móvil antiguo como GPS de senderismo. Nada del diseño necesita una versión posterior; lo que cambia por versión son permisos:

| Pieza | Android 12 | Android 13 | Android 14 o superior |
| --- | --- | --- | --- |
| Notificación del servicio | Sin permiso | `POST_NOTIFICATIONS` | `POST_NOTIFICATIONS` |
| Servicio de ubicación | `foregroundServiceType="location"` | Igual | Además `FOREGROUND_SERVICE_LOCATION` |
| Ubicación en segundo plano | `ACCESS_BACKGROUND_LOCATION` en una pantalla de ajustes aparte | Igual | Igual |
| Exención de batería | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (diálogo del sistema) | Igual | Igual |
| Nearby Connections | Bluetooth y ubicación precisa | Bluetooth y `NEARBY_WIFI_DEVICES` | Igual que 13 |
| Selector de ficheros | Selector del sistema (sin permisos) | Igual | Igual |

- MapLibre, Room, Play Services y HCE soportan versiones muy anteriores, así que no limitan.
- Los móviles antiguos tienen menos memoria y GPU más lenta: la niebla por ventana visible y niveles de detalle está pensada también para ellos. Hay que probar al menos en un dispositivo real con Android 12.
- Bajar el mínimo, por ejemplo a Android 8 (API 26), sería posible, pero exigiría ramas de permisos de Bluetooth anteriores a la 12; no se hace mientras nadie lo necesite.

## Estado actual y limitaciones

El código del PoC se reescribió desde cero el 24 oct 2026 siguiendo este documento: Gradle 9.6 con AGP 9.4 (Kotlin integrado en AGP), KSP 2.3.6, Room 2.7.2, MapLibre 11.13, Compose BOM 2026.06.01 (la última que compila con `compileSdk 36`), `minSdk 31` y `compileSdk 36`. Incluye las tres funciones del PoC, la niebla por capas con niveles de detalle, el servicio en primer plano, la selección múltiple de GPX y los textos en inglés y castellano. Aún no se ha compilado ni probado en un dispositivo, y las pruebas de JVM (`geo/` y lector de GPX) están escritas pero no se han ejecutado; es esperable ajustar versiones en la primera sincronización.

**Desajustes con lo pedido**

- Ninguno. El fichero `spain.mbtiles` del mapa general se genera con `tools/build_spain_mbtiles.py` antes de compilar.

**Limitaciones conocidas**

- Los mapas solo se ven entre el zoom mínimo y el máximo descargados: por debajo el área aparece vacía (se ve el mapa general) y por encima se amplía el último nivel.
- La configuración de la rejilla (25 m de celda, 30 m de radio) está fijada en el código.
- La descarga de tiles es secuencial y sin reintentos.
- Desde la versión 3 de la base de datos los cambios de esquema llevan migración (la 2→3 crea la tabla `track`); la migración destructiva solo cubre versiones sin ella.

## Hoja de ruta y decisiones

El siguiente paso lógico es generar el mapa general y probar la app en un dispositivo. El resto sigue el orden original del proyecto, salvo la preparación del reparto (paso 5), que es nueva.

1. Generar `spain.mbtiles` con `tools/build_spain_mbtiles.py` y comprobar su tamaño.
2. Primera sincronización y compilación, pasar las pruebas de JVM y probar la niebla, los mapas offline, la importación y el seguimiento en un dispositivo con Android 12.
3. Conseguir una muestra del export de Cronología del móvil y escribir su parser.
4. Hecho (decisión 0011): pantalla de ajustes con valores de lista y reprocesamiento al salir, que también recalcula `revealedCount`; el modo de niebla a zoom bajo es un ajuste avanzado. Falta el alias, que llega con el intercambio (paso 6).
5. Antes de repartir el primer APK: clave de firma definitiva y paso a migraciones reales de Room.
6. Implementar el intercambio por NFC y Nearby Connections con GPX (columnas de huella y alias en la tabla `track`, selección «Todas / Seleccionar» y resaltado activable de las celdas importadas).
7. Añadir reintentos a la descarga.
8. Portar a iOS: mover `geo/`, `data/` y `core/` a un módulo `shared` de KMP, con Room KMP, y convertir la creación de la base de datos en `expect/actual`.

**Decisiones cerradas** (24 oct 2026)

- Formato de Google Maps: el export de Cronología del móvil; Takeout antiguo no se soporta.
- Mapa general de España: un MBTiles de zoom 5–9 incluido en el APK y servido por el servidor local.
- Servidor local de tiles: se mantiene. MapLibre lee PMTiles locales desde la 11.7.0 ([documentación](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)), pero con menos de 100 usuarios la migración no compensa.
- Intercambio: NFC para el contacto y Nearby Connections para los datos; selección «Todas / Seleccionar»; sin reenvío de rutas de terceros; se guarda el alias del emisor, que se pide en el primer envío; el receptor descarta duplicados por huella.
- Vista: siempre todas las áreas, con un resaltado sutil y activable de las celdas importadas.
- Ajustes: radio y celda de listas cerradas; reprocesamiento una sola vez al salir de Ajustes, con un aviso bajo cada valor cambiado (sustituye a «al guardar, tras confirmar»).
- Interfaz en inglés con traducción al castellano (`values-es`).
- Seguimiento con servicio en primer plano dentro del PoC.
- Migración destructiva solo hasta repartir la primera versión; después, migraciones reales.
- Distribución: APK firmado directo, siempre con la misma clave.
- Niebla por capas: cubre toda la pantalla; se calcula solo para la ventana visible con margen y con el nivel de detalle que corresponde al zoom.
- Persistencia: Room KMP (≥ 2.7) en lugar de migrar a SQLDelight; `data/` se escribe compatible desde hoy.
- Interfaz: Jetpack Compose con Material 3 y paleta fija de marca (no color dinámico); el mapa sigue como vista nativa dentro de `AndroidView`. Solo vertical. Barras flotantes con iconos en las pantallas de mapa, ayuda en un `(?)` y capturas de referencia con Roborazzi. Compose Multiplatform o SwiftUI se decide al portar a iOS.
- Lógica compartible: lo que iOS también necesitaría vive en `core/`, `geo/` y `data/`, sin Android; pantallas y servicios solo hacen de pegamento con la plataforma. Sin módulo `shared` ni interfaces hasta portar.
- Versión mínima: Android 12 (API 31).
- Marca: la app se llama «Hike Explorer» para el usuario; FogHike se mantiene como nombre interno (paquete, `applicationId`, clases, ficheros y documentación técnica).
