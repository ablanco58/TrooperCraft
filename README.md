# TroperCraft

Mod para **Minecraft Java 1.20.1 con Fabric** que añade patrullas hostiles de **3 a 5 stormtroopers** al Overworld. Caminan en grupo, llevan blásteres y combaten con proyectiles láser rojos, efectos de sonido y frases de voz.

## Comportamiento

- Grupos completos de 3, 4 o 5 miembros, con un líder y seguidores en formación.
- Aparición progresiva según las noches del mundo: primera tranquila, segunda intermedia y tercera intensa, con un límite estable desde entonces. Pueden aparecer de día y de noche, incluidas cuevas iluminadas.
- **20 puntos de vida (10 corazones)** por trooper. El sol no les prende fuego.
- Los troopers causan **3 puntos de daño base (1,5 corazones)** por impacto: equivalente al ataque de un zombi sin arma en dificultad Normal contra un jugador sin protección.
- El bláster del jugador causa **10 puntos de daño (5 corazones)** por impacto: mata a un creeper normal con **2 disparos**, sin defensas ni efectos adicionales.
- Los troopers tienen un **40 % de probabilidad de soltar un bláster** al morir. Tiene **131 puntos de durabilidad**, como una espada de piedra, y gasta uno por disparo o golpe cuerpo a cuerpo.
- Un disparo cada **30 ticks (1,5 segundos a 20 TPS)**, con alcance de combate de 24 bloques.
- **5 sonidos E-11**, recortados por disparo, elegidos aleatoriamente al disparar.
- Atacan jugadores en Supervivencia/Aventura y otros mobs, incluidos animales, aldeanos y criaturas de otros mods que usen las clases habituales de Minecraft.
- Todos los stormtroopers son aliados, incluso entre patrullas distintas. Los disparos de troopers atraviesan a sus compañeros sin dañarlos; **el bláster del jugador sí daña a los troopers**. Los láseres chocan con los bloques sólidos.
- Los supervivientes eligen otro líder si pierden al anterior. La identidad de patrulla y las posiciones se guardan con el mundo.
- **Cabeza de trooper equipable**: sirve de disfraz para que te ignoren; se fabrica con hierro y carbón o se consigue con un **30 % de probabilidad al matar un trooper**.
- **21 clips de voz**: pausas de **8–18 segundos al patrullar** y de **1–3 segundos al combatir**, después de terminar la frase actual.
- Voces al azar según el contexto, sin repetir inmediatamente el mismo clip. Cerca pueden hablar a la vez un trooper en patrulla o dos en combate.
- Voces audibles hasta **48 bloques** y disparos hasta **64 bloques**, con sonido 3D que aumenta gradualmente al acercarse y disminuye al alejarse.
- Efectos de disparo, impacto, daño y muerte; interfaz y subtítulos descriptivos en español e inglés.
- Bláster 3D inspirado en la referencia E-11: cañón largo ventilado, mira telescópica, empuñadura y culata abierta. El jugador y todos los troopers comparten el mismo modelo.

La formación se adapta al terreno mediante navegación normal: puede romperse temporalmente en lugares estrechos. Los grupos nacen completos; las bajas posteriores no se reponen. Las voces en inglés proceden del [video de stormtroopers indicado para este proyecto](https://www.youtube.com/watch?v=BQj6apGaxOY) y los disparos del [video del rifle E-11](https://www.youtube.com/watch?v=ZfUJvRCJpr4). Consulta [AUDIO_CREDITOS.txt](AUDIO_CREDITOS.txt).

## Instalación

1. Instala Minecraft **1.20.1**, Fabric Loader **0.20.1 o superior compatible con 1.20.1** y Fabric API para esa versión. El proyecto se ha probado con **Fabric API 0.92.12+1.20.1**.
2. Coloca `tropercraft-mod-1.0.0.jar` en `mods/`. No uses el archivo `-sources.jar`.
3. En multijugador, instala el mod y Fabric API tanto en el servidor como en los clientes.
4. Abre el Overworld en Fácil, Normal o Difícil, con `/gamerule doMobSpawning true`.

El mod se compila para **Java 17**. La configuración de desarrollo y CI usa **JDK 25** para ejecutar el Gradle incluido.

## Aparición y configuración

Al iniciar se crea `config/tropercraft.json` en la instalación del juego o servidor:

```json
{
  "spawnIntervalSeconds": 10,
  "spawnChance": 1.0,
  "patrolsPerCycle": 2,
  "maxTroopersPerPlayer": 40,
  "maxTroopersPerWorld": 160,
  "laserDamage": 3.0,
  "playerLaserDamage": 10.0,
  "shotIntervalTicks": 30,
  "enableVoices": true,
  "voiceVolume": 0.85,
  "minPatrolVoicePauseSeconds": 8,
  "maxPatrolVoicePauseSeconds": 18,
  "minCombatVoicePauseSeconds": 1,
  "maxCombatVoicePauseSeconds": 3
}
```

Con la configuración anterior, la presión aumenta así:

| Etapa | Búsquedas por jugador | Probabilidad por búsqueda | Límite cercano por jugador | Techo del mundo |
| --- | --- | --- | --- | --- |
| Inicio y primera noche | 1 cada 60 segundos | 45 % | 6 troopers | 24 |
| Segunda noche | 1 cada 30 segundos | 75 % | 12 troopers | 48 |
| Tercera noche y siguientes | 2 cada 20 segundos | 100 % | 24 troopers | 96 |

Estos son límites de población simultánea para autorizar nuevas patrullas, no
cantidades garantizadas por noche. Los grupos siguen siendo de 3 a 5 miembros.
Se usa el calendario del Overworld: la segunda etapa empieza en el tick 37000,
la tercera en el 61000. Dormir cuenta el avance del calendario; amanecer no sube
la etapa por sí solo. Los mundos antiguos comienzan en su etapa correspondiente.
Después de la tercera noche la presión deja de aumentar. Cambiar el calendario
con `/time set` también cambia la etapa.

La búsqueda alterna su preferencia entre superficie y cuevas; si no encuentra
espacio prueba el otro entorno. Pueden aparecer de día, de noche y con iluminación
artificial en todos los biomas del Overworld.

El centro se busca a 28–64 bloques en horizontal, dentro de 96 bloques de distancia del jugador. Las cuevas se buscan en toda la altura cercana que permita esa distancia. Se admiten caminos de tierra, cultivos, losas y escaleras, además de los suelos completos. Ningún miembro aparece a menos de 24 bloques de un jugador. Hace falta terreno transitable y espacio para el grupo completo, dentro de chunks cargados.

El límite global natural es el menor entre el techo de la etapa y su límite por
jugador multiplicado por los jugadores elegibles. También se aplica el límite
por jugador en una caja de 96 bloques alrededor. Se cuentan los troopers cargados;
estos límites son propios del mod e independientes del cupo de otros monstruos.
Si no cabe el grupo completo o se alcanzó un límite, se omite esa oportunidad.

Los límites configurados actúan como techos adicionales: bajarlos reduce la
población de cualquier etapa. El intervalo base se multiplica por 6, 3 o 2 y la
probabilidad por 0,45, 0,75 o 1 según la etapa. Las búsquedas se limitan a 1, 1 o 2,
sin superar `patrolsPerCycle`. La progresión también se aplica a configuraciones
antiguas. `spawnChance=0` sigue impidiendo nuevas apariciones automáticas.

Los cambios requieren **reiniciar el juego o servidor**. En multijugador manda la configuración del servidor. Pacífico, `doMobSpawning=false` y `spawn-monsters=false` bloquean las apariciones automáticas. No aparecen naturalmente en el Nether ni en el End. Conservan las reglas de desaparición de los mobs hostiles y no mantienen chunks cargados.

`laserDamage` controla el daño de los troopers; `playerLaserDamage` controla el del
jugador. Las configuraciones antiguas sin `playerLaserDamage` usan 10 por defecto.

Cada trooper tiene sus propios tiempos y empieza a hablar tras un retardo aleatorio. Al detectar un enemigo acelera su próxima frase, respetando la que ya esté reproduciendo. Los intervalos son pausas después del clip, no su duración total; esperar turno entre vecinos puede alargarlos. `enableVoices=false` desactiva las frases y `voiceVolume` admite valores de 0 a 1. Las configuraciones anteriores funcionan con los valores nuevos predeterminados aunque todavía no tengan esos campos.

Las voces comparten turnos entre troopers dentro de 48 bloques para reducir la
superposición. Tanto las voces como los disparos se reproducen desde su posición
real y se atenúan con la distancia; ampliar el alcance no aumenta el volumen
máximo cercano. El alcance de los disparos también se aplica al bláster del jugador.

Consulta [DETALLES_DEL_MOD.txt](DETALLES_DEL_MOD.txt) para conocer los límites de configuración, arquitectura, decisiones, pruebas y comprobaciones manuales.

## Comandos y objetos de prueba

Con trucos habilitados o permisos de operador de nivel 2, sobre suelo amplio en el Overworld:

```mcfunction
/difficulty normal
/gamerule doMobSpawning true
/stormpatrol 3
/stormpatrol 4
/stormpatrol 5
/summon tropercraft-mod:stormtrooper ~ ~ ~
/give @s tropercraft-mod:stormtrooper_spawn_egg
/give @s tropercraft-mod:blaster
/give @s tropercraft-mod:trooper_head
```

`/stormpatrol` sin número crea 3 miembros; busca espacio unos 6 bloques delante del ejecutor. Las patrullas manuales respetan dimensión, dificultad y espacio, pero omiten probabilidad y límites de población automática. `/summon` y el huevo generan **un solo trooper** para inspección o construcción de escenarios.

El bláster y la cabeza figuran en Combate y el huevo en Huevos generadores. El
jugador dispara con clic derecho, sin munición, y consume un punto de durabilidad
por disparo. Se rompe al agotar sus 131 usos; los golpes cuerpo a cuerpo también
gastan uno. Creativo no consume durabilidad. No tiene receta: se consigue con un
40 % de probabilidad al morir un trooper, con la durabilidad completa. Este sorteo
es independiente del de la cabeza, no aumenta con Saqueo y respeta `doMobLoot`.

## Cabeza de trooper y disfraz

Se fabrica en una mesa de crafteo con **5 lingotes de hierro y 1 carbón mineral**:

```text
Hierro   Hierro   Hierro
Hierro   Carbón   Hierro
Vacío    Vacío    Vacío
```

También hay un **30 % de probabilidad por baja atribuida a un jugador** de que un stormtrooper suelte una cabeza. Es un sorteo independiente por baja; Saqueo no aumenta ese porcentaje y las muertes sin jugador no la sueltan. El botín respeta `doMobLoot`.

Equípala en la ranura de la cabeza o usa clic derecho como con un casco. Tiene las estadísticas, durabilidad y reparación del casco de hierro, y muestra la cabeza del trooper usando la skin existente. La receta aparece en el libro al conseguir hierro y carbón.

La textura del objeto se registra también en el atlas del inventario para que el modelo use la skin correcta. El bláster del jugador causa su daño normal a los troopers tanto con disfraz como sin él; sus disparos conservan ese origen al guardarse con el mundo.

Mientras la lleves equipada los troopers te ignoran, incluso si ya te estaban persiguiendo o los golpeas. Los láseres disparados por troopers pasan sin dañarte mientras mantengas el disfraz. Al quitarla o romperse, vuelves a ser un objetivo. Tenerla solo en la mano o el inventario no activa el disfraz; los demás enemigos conservan su comportamiento habitual.

## Desarrollo

Usa **JDK 25** para reproducir el entorno probado. Configura `JAVA_HOME` a ese JDK o indica su ruta con `-Dorg.gradle.java.home=/ruta/al/jdk-25`. No se fija ninguna ruta personal de Java en el repositorio.

```bash
# Compilar el mod instalable
./gradlew build

# Ejecutar pruebas de integración dentro de Minecraft, sin ventana
./gradlew runGametest

# Iniciar el cliente de desarrollo
./gradlew runClient
```

En Windows usa `gradlew.bat`. La primera ejecución necesita conexión para descargar dependencias. Con la caché preparada puede añadirse `--offline`.

El JAR instalable queda en `build/libs/tropercraft-mod-1.0.0.jar`. Las pruebas están en `src/gametest/`, usan un mundo aislado en `build/gametest/` y escriben `build/reports/gametest.xml`. El mod de pruebas no se incluye en el JAR distribuible. GitHub Actions ejecuta la compilación y los GameTests.

Para regenerar los sonidos, con Python 3 y `ffmpeg` instalados:

```bash
python3 tools/generate_sounds.py
```

Los `.ogg` ya están incluidos; Python y ffmpeg no son requisitos para jugar ni compilar.

El modelo del bláster ya está incluido en `assets/tropercraft-mod/models/item/blaster.json`.
Se puede regenerar con `python3 tools/generate_blaster_model.py`: usa geometría nativa
y texturas de Minecraft, sin paquetes de recursos ni librerías adicionales.

`tools/import_voice_lines.py` prepara las voces desde una grabación local con ffmpeg: detecta pausas, recorta, nivela el volumen, convierte a Ogg mono y genera `voice_lines.json` y los eventos de sonido. El manifiesto guarda la fuente, los tiempos de corte y el contexto de cada clip. El TXT detalla cómo reproducir la importación. La grabación completa y los subtítulos del video no se incluyen en el repositorio.

`tools/import_blaster_shots.py` prepara los disparos E-11 de la misma manera, conservando un disparo aislado por archivo. Los cinco `.ogg` están en `assets/tropercraft-mod/sounds/blaster/`; `blaster_shots.json` documenta los cortes y la procedencia. El evento `blaster_shoot` los usa tanto para troopers como para el arma del jugador.

## Organización

```text
src/main/java/com/tunombre/stormcraft/
├── TroperCraftMod.java          # Inicialización y registros comunes
├── client/                     # Inicializador y renderizado del cliente
├── config/                     # Configuración del servidor
├── entity/                     # Stormtrooper, proyectil y registros
│   └── ai/                     # Marcha y formación de seguidores
├── item/                       # Bláster, cabeza equipable y huevo generador
├── sound/                      # Registro de sonidos
└── world/                      # Aparición de patrullas y comando

src/main/resources/
├── fabric.mod.json
├── assets/tropercraft-mod/      # Skin, modelos, idiomas y sonidos
└── data/tropercraft-mod/        # Botín, receta y desbloqueo en el libro

src/gametest/                   # Pruebas con servidor real
tools/generate_sounds.py        # Generación reproducible de audio
tools/import_voice_lines.py     # Recorte y preparación del banco de voces
tools/import_blaster_shots.py   # Recorte por disparo del rifle E-11
DETALLES_DEL_MOD.txt            # Explicación completa en español
```

## Alcance actual

Esta versión implementa patrullas, combate, voces contextuales y una cabeza de disfraz con receta y botín. No incluye estructuras imperiales, vehículos ni misiones. Los GameTests comprueban el combate, las voces, la receta, el disfraz y el botín; el aspecto y la mezcla final de audio requieren también revisión jugando en un cliente.

Proyecto no oficial inspirado en Star Wars. La licencia del código del repositorio es [CC0-1.0](LICENSE); las voces y los disparos de terceros no se incluyen en esa declaración de licencia.
