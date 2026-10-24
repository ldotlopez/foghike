# Pruebas en persona

Lo que no se puede comprobar con adb ni con pruebas de JVM: requiere caminar, girar el móvil, varios dedos o el otro teléfono. Marca cada punto al probarlo y anota lo raro en `ISSUES.md`.

## En una caminata (S10e, Android 12)

- [ ] **Pantalla apagada durante toda la ruta.** Al volver, la ruta no tiene huecos salvo bajo techo o en zonas sin cielo. El hueco de 15 min del 24 oct fue por pérdida de satélites, no de la app.
- [ ] **Flecha de orientación.** Girando sobre ti mismo, la flecha apunta hacia donde miras y no va con retraso ni da saltos. Si se desvía, calibra la brújula (ocho en el aire) y repite.
- [ ] **Rastro.** Aparece detrás de ti mientras caminas (tarda hasta 10 s) y el tramo de hace una hora casi ha desaparecido.
- [ ] **Apagar la ubicación a mitad de ruta.** La notificación avisa; al volver a activarla sigue registrando en la misma ruta.
- [ ] **Niebla parcial a zoom bajo.** Alejando el mapa, la zona recorrida se ve más clara que el resto pero no del todo despejada; acercando, el camino se ve limpio.
- [ ] **Batería.** Anota el porcentaje al empezar y al terminar, con la pantalla apagada casi todo el tiempo. Repite un rato con la pantalla encendida (GPS y brújula de la flecha) para comparar.

## Mapas offline

- [ ] **Descarga con datos móviles,** sin wifi.
- [ ] **Perder la conexión a mitad de descarga** (modo avión unos segundos): el texto pasa a «esperando conexión» y sigue sola al volver.
- [ ] **Dejar una descarga larga y bloquear el móvil:** termina, y el área aparece al volver a la pantalla principal.
- [ ] **Línea horizontal en la niebla** a zoom ~11 (ver `ISSUES.md`): si vuelve a salir, haz una captura e indica el zoom.

## En el S24 (Android 16)

- [ ] **Primer arranque:** permisos de ubicación precisa, notificaciones, «Permitir todo el tiempo» y exención de batería.
- [ ] **Exploración con la pantalla apagada:** el servicio sigue y la notificación se ve.
- [ ] **Descarga en segundo plano:** aparece la notificación de progreso con Cancelar (Android 14+ exige el tipo de servicio de la descarga).
- [ ] **Importar GPX** desde otras apps (Drive, Archivos, un adjunto de correo).

## Otros

- [ ] **Móvil en catalán:** la app sale en inglés; decide si añadir catalán o cambiar el idioma por defecto.
- [ ] **Que Android mate la app explorando** (por ejemplo, abriendo muchas apps pesadas): la exploración sigue sola en una ruta nueva.
