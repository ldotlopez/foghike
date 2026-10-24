# Plan

## Fog

- **Animated fog (optional).** Setting to show real-looking moving fog, generated procedurally (e.g. scrolling/evolving noise), only while the screen is on and the map is visible; stop animating when the screen is off or the app is in the background. Default stays the current static texture to save battery.
- **Soft fog edges.** The fog still ends in hard cell-shaped edges; a feathered border would look more natural.
- **Less visible fog pattern.** At low zoom the 256 px texture visibly repeats as a grid of blobs; use a larger texture or broader noise (lower-frequency octaves).

## UI

- **Shorter texts.** Button labels that wrap to two lines push the button row up (e.g. Spanish "Mover mapa" in the download screen). Shorten them: "Start exploring" → "Explore" (es: "Iniciar exploración" → "Explorar"), and review the rest.
- **Polish phase (later).** Replace text buttons with icons and add visual cues.
