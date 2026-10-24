# Problemas conocidos

Problemas y limitaciones del PoC detectados al escribirlo o al probarlo en un Galaxy S10e (Android 12) y un Galaxy S24 (Android 16). Lo pendiente por diseño (ajustes, reprocesamiento, Google Maps, NFC, iOS) está en la hoja de ruta del documento de diseño, no aquí.

## Seguimiento GPS

- **Reinstalar o actualizar la app corta la exploración.** Android no reinicia el servicio tras una instalación; la sesión queda cerrada en el último punto guardado.
- **Sin exención de batería o sin «Permitir todo el tiempo»,** si Android mata el servicio, no puede reiniciarlo en primer plano y se detiene (limpiamente, sin fallo). Se respeta la decisión del usuario; la exención se vuelve a pedir en cada inicio.
- **Ahorro de energía de Samsung.** El modo ahorro y la lista de «aplicaciones en suspensión» pueden reducir el GPS aunque la app esté exenta de la optimización de batería. No probado en una caminata real con la pantalla apagada.
- **Cada reinicio del servicio abre una ruta nueva** (`live-<hora>`). No se pierden puntos, pero una caminata puede quedar partida en varias rutas.

## Descarga de mapas

- **El número de tiles se calcula en el hilo principal** al pulsar Descargar. Con 115.000 tiles tardó menos de 4 s, pero un área mayor bloquearía la pantalla un momento.
- **Sin reintentos.** Un tile que falla se omite y solo queda en el log.
- **Un área descargada no aparece en la pantalla principal hasta volver a ella** (se recargan los mapas en `onResume`). Si la descarga termina mientras el usuario ya está en la principal, no se ve hasta salir y volver.
- **Si el proceso muere a mitad de una descarga, el área empieza de cero** al reanudarse.
- **Un área solo se ve entre su zoom mínimo y máximo.** Por debajo se ve el mapa general; por encima se amplía el último nivel.

## Interfaz

- **Idiomas sin traducción caen al inglés.** Con el móvil en catalán (`ca-ES`) la app sale en inglés. Pendiente decidir entre añadir `values-ca` o cambiar el idioma por defecto.
- **Posible línea horizontal en la niebla** a zoom intermedio (vista una vez a zoom ~11). Puede ser un borde de tile del mapa general ampliado o una costura entre rectángulos de niebla; sin confirmar.

## Distribución

- **APK de 65 MB.** Incluye las librerías nativas de MapLibre para cuatro arquitecturas. Limitarlo a `arm64-v8a` y `armeabi-v7a` lo dejaría en unos 35 MB, a costa de no poder usar el emulador x86_64.
- **Migración destructiva de Room.** Aceptable hasta repartir el primer APK; después cada cambio de esquema necesita su migración (paso 5 de la hoja de ruta).
