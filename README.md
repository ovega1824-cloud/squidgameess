# Squid Game – Luz verde, luz roja (Forge 1.20.1)

## Uso
1. Crea (o toma del inventario creativo, pestaña **Squid Game**) el **Bloque 1** y ponlo donde empieza el juego.
2. Pon el **Bloque 2** donde está la meta. Esa es la línea: quien la pase (más allá del bloque, en la dirección del bloque 1 al 2) ya pasó el juego.
3. `/sg j1 start <tiempo>` — el tiempo acepta `90`, `90s`, `2m`, `1m30s`, `1h`. Sin tiempo = 5 minutos.
4. `/sg j1 stop` termina el juego y revive a los eliminados. `/sg j1 revive` revive a los eliminados después de que acabó.

## Reglas
- Luz verde: puedes moverte (suena el sonido 1 para todos).
- Luz roja: suena el sonido 2 para todos. Saltar, moverte, mover la cámara o golpear = eliminado (espectador + sonido del disparo).
- Pasar la línea del Bloque 2: pantalla verde + sonido de éxito, y ya no te afecta la luz roja.
- Al acabar el tiempo, los que no pasaron quedan en espectador.

## Compilar el .jar en GitHub
Sube todo este proyecto a un repositorio. GitHub Actions lo compila solo y deja el `.jar` en **Releases** (y en Actions → Artifacts).

## Audífonos (v1.1.0)
- Ítem **Audífonos** (pestaña Squid Game). Clic derecho = abre el menú; al elegir una canción la oyen todos los jugadores. No sale nada en el chat ni en la pantalla.
- Carpeta `.minecraft/sound`: se crea sola. Pon ahí tus `.mp3` y `.wav`, o arrástralos al menú, o pega la ruta y pulsa Importar.
- Ajustes (menú → Ajustes): volumen, volumen personal, fade in/out, empezar en el segundo X, velocidad, radio, repetir, cortar el anterior, solo yo, silenciar, cerrar al elegir, mostrar tamaños, orden por fecha. Se guardan en `config/squidgame_headphones.json`.
- Servidor (`serverconfig/squidgame-server.toml` del mundo): `onlyOps` y `maxFileSizeMb`.
- .mp4/.m4a (AAC) todavía no están soportados.

## Muerte en el minijuego
Al ser eliminado se ejecuta un /kill; al reaparecer vuelves al lugar donde moriste y en modo espectador.

## Puente de cristal (v1.2.0)
1. Pon el **Bloque 1** (inicio) y el **Bloque 2** (meta), igual que en luz verde/luz roja.
2. Construye el puente con el ítem **Cristal del puente (2x2)**: cada uso coloca un panel de 2x2 de losas de cristal.
3. Con el **Palo verde** haz clic derecho en un bloque (punto 1) y en otro (punto 2): la zona entre los dos puntos es el puente.
4. `/sg puntedecristal start <tiempo>` (también `/sg puentedecristal ...`). Sin tiempo = 5 minutos.
   - En cada fila de paneles uno es resistente y los demás son falsos, al azar. Todos se ven iguales.
   - Al pisar uno falso quedas congelado medio segundo, se rompe (partículas blancas y sonido de cristal) y caes: eliminado con /kill.
   - Al acabar el tiempo se rompen todos los cristales y mueren los que no pasaron el Bloque 2.
   - Quien pasa el Bloque 2 gana (pantalla verde y sonido de éxito).
5. `/sg puntedecristal stop` detiene, revive y arregla el puente; `restore` solo arregla el puente; `revive` revive a los eliminados.

## La final (v1.3.0)
1. **Bloque F** (pestaña Squid Game): con ellos haz la forma del área de pelea. Si la forma es un contorno cerrado, todo lo de adentro también cuenta como área.
2. `/sg final set <jugador1> <jugador2>`: reparte 3 vidas a cada uno. Arriba de la pantalla salen sus nombres y las barras (roja = jugador 1, azul = jugador 2).
3. `/sg final start`: empieza (suena el disparo). Los peleadores reciben el modelo de la final con su textura. `/sg final stop` la detiene.
4. Cada vez que te sacan del área pierdes una vida y vuelves adentro. Con 0 vidas: /kill y reapareces en espectador donde moriste.
5. Con 1 corazón (2 de vida) caes al suelo: muy lento, sin saltar, cámara con zoom y animación "tirado". Al recuperar más de 3 corazones te vuelves a parar.
6. Un jugador junto a ti puede cargarte si mantiene ESPACIO 8 segundos. Clic izquierdo = te lanza unos 4 bloques, clic derecho = te suelta.
7. Carpeta `.minecraft/squidgame_assets`: pon imágenes `001.png`, `002.jpg`, etc. `/sg final ganador set 001` elige la imagen y `/sg final ganador show` la muestra a todos (la sube quien ejecuta el comando). `/sg final ganador hide` la quita.
Config del servidor (`squidgame-server.toml`): `modelForEveryone`, `lives`, `downHealth`, `recoverHealth`, `winnerImageSeconds`.
