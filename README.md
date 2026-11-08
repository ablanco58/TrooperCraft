# TroperCraft

Mod para **Minecraft Java 1.20.1 con Fabric** que añade patrullas hostiles de **3 a 5 stormtroopers** al Overworld. Caminan en grupo, llevan blásteres y combaten con proyectiles láser rojos, efectos de sonido y frases de voz.

## Comportamiento

- Grupos completos de 3, 4 o 5 miembros, con un líder y seguidores en formación.
- Aparición progresiva durante cinco noches: empieza tranquila y gana presión hasta la quinta, con un límite estable desde entonces. Pueden aparecer de día y de noche, incluidas cuevas iluminadas.
- **20 puntos de vida (10 corazones)** por trooper. El sol no les prende fuego.
- Los troopers causan **3,5 puntos de daño base (1,75 corazones)** por impacto contra un jugador sin protección en dificultad Normal.
- El bláster del jugador causa **8 puntos de daño (4 corazones)** por impacto: mata a un creeper normal con **3 disparos**, sin defensas ni efectos adicionales.
- Primera patrulla sin sorteo: busca aparecer delante del jugador al entrar por primera vez en el Overworld y reintenta hasta encontrar suelo seguro y visible.
- Los troopers tienen un **15 % de probabilidad de soltar un bláster** al morir. Tiene **32 puntos de durabilidad**, como una espada de oro, y gasta uno por disparo o golpe cuerpo a cuerpo.
- Un disparo cada **30 ticks (1,5 segundos a 20 TPS)**, con alcance de combate de 24 bloques.
- **5 sonidos E-11**, recortados por disparo, elegidos aleatoriamente al disparar.
- Atacan jugadores en Supervivencia/Aventura y otros mobs, incluidos animales, aldeanos y criaturas de otros mods que usen las clases habituales de Minecraft.
- Todos los stormtroopers son aliados, incluso entre patrullas distintas. Los disparos de troopers atraviesan a sus compañeros sin dañarlos; **el bláster del jugador sí daña a los troopers**. Los láseres chocan con los bloques sólidos.
- Los supervivientes eligen otro líder si pierden al anterior. La identidad de patrulla y las posiciones se guardan con el mundo.
- **Cabeza de trooper equipable**: sirve de disfraz para que te ignoren; se fabrica con hierro y carbón o se consigue con un **15 % de probabilidad al matar un trooper**. Cada trooper suelta como máximo un objeto: casco o bláster, nunca ambos.
- **21 clips de voz**: pausas de **8–18 segundos al patrullar** y de **1–3 segundos al combatir**, después de terminar la frase actual.
- Voces al azar según el contexto, sin repetir inmediatamente el mismo clip. Cerca pueden hablar a la vez un trooper en patrulla o dos en combate.
- Voces audibles hasta **48 bloques** y disparos hasta **64 bloques**, con sonido 3D que aumenta gradualmente al acercarse y disminuye al alejarse.
- Efectos de disparo, impacto, daño y muerte; interfaz y subtítulos descriptivos en español e inglés.
- Bláster 3D inspirado en la referencia E-11: cañón largo ventilado, mira telescópica, empuñadura y culata abierta. El jugador y todos los troopers comparten el mismo modelo.

La formación se adapta al terreno mediante navegación normal: puede romperse temporalmente en lugares estrechos. Los grupos nacen completos; las bajas posteriores no se reponen. Las voces en inglés proceden del [video de stormtroopers indicado para este proyecto](https://www.youtube.com/watch?v=BQj6apGaxOY) y los disparos del [video del rifle E-11](https://www.youtube.com/watch?v=ZfUJvRCJpr4). Consulta [AUDIO_CREDITOS.txt](AUDIO_CREDITOS.txt).

## Instalación

1. Instala Minecraft **1.20.1**, Fabric Loader **0.20.1 o superior compatible con 1.20.1** y Fabric API para esa versión. El proyecto se ha probado con **Fabric API 0.92.12+1.20.1**.
2. Coloca `tropercraft-mod-1.1.0.jar` en `mods/`. No uses el archivo `-sources.jar`.
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
  "balanceVersion": 1,
  "laserDamage": 3.5,
  "playerLaserDamage": 8.0,
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
| Inicio y primera noche | 1 cada 15 segundos | 40 % | 6 troopers | 24 |
| Segunda noche | 1 cada 15 segundos | 50 % | 10 troopers | 40 |
| Tercera noche | 2 cada 15 segundos | 60 % | 14 troopers | 56 |
| Cuarta noche | 2 cada 15 segundos | 70 % | 20 troopers | 80 |
| Quinta noche y siguientes | 2 cada 15 segundos | 80 % | 24 troopers | 96 |

**Refuerzo al explorar:** al entrar en un chunk que todavía no se haya visitado
desde esta actualización, se habilita una búsqueda adicional con un **95 % de
probabilidad** con la configuración inicial. Puede intentarse como máximo una vez
cada **5 segundos**, sin esperar al ciclo habitual de 15 segundos. Busca hasta
48 posiciones a 28–52 bloques, priorizando la zona delante del jugador y el
suelo de su misma altura, tanto en superficie como en cuevas.

El refuerzo respeta los mismos límites de población y reglas de aparición.
Si falta suelo seguro, espacio o chunks cargados, puede reintentarlo durante
15 segundos. Cruzar muchos chunks rápidamente agrupa las oportunidades; no
crea una patrulla por cada chunk. La lista de chunks visitados se guarda por
mundo y se comparte entre jugadores: volver a una zona o reconectar no vuelve
a activar su refuerzo. Cargar la vista inicial sin moverse tampoco lo activa.
`spawnChance` escala su 95 % y, si vale 0, también desactiva estos refuerzos.

Además, tras unos 3–5 segundos de la primera entrada de cada jugador, se busca
una patrulla de 3 miembros a 28–40 bloques delante, sin sorteo y con visión directa.
Si no hay suelo seguro, espacio o chunks cargados, se reintenta cada 2 segundos.
Si ya hay un trooper visible delante, el encuentro se considera cumplido. Se
guarda por jugador y mundo para no añadir otra patrulla al reconectar. Este
encuentro tiene prioridad sobre los límites de población, pero respeta Pacífico,
`doMobSpawning`, `spawn-monsters` y `spawnChance=0`.

Estos son límites de población simultánea para autorizar nuevas patrullas, no
cantidades garantizadas por noche. Los grupos siguen siendo de 3 a 5 miembros.
Se usa el calendario del Overworld: la segunda etapa empieza en el tick 37000,
la tercera en el 61000, la cuarta en el 85000 y la quinta en el 109000.
Dormir cuenta el avance del calendario; amanecer no sube
la etapa por sí solo. Los mundos antiguos comienzan en su etapa correspondiente.
Después de la quinta noche la presión deja de aumentar. Cambiar el calendario
con `/time set` también cambia la etapa.

La búsqueda alterna su preferencia entre superficie y cuevas; si no encuentra
espacio prueba el otro entorno. Pueden aparecer de día, de noche y con iluminación
artificial en todos los biomas del Overworld.

Cuando hay otros mobs vivos en tierra firme a 28–64 bloques, se dedica el 75 %
de los intentos a sus alrededores. Así se favorecen zonas con actividad de mobs;
el resto de los intentos sigue buscando terreno normal.

El centro se busca a 28–64 bloques en horizontal, dentro de 96 bloques de distancia del jugador. Las cuevas se buscan en toda la altura cercana que permita esa distancia. Se admiten caminos de tierra, cultivos, losas y escaleras, además de los suelos completos. Ningún miembro aparece a menos de 24 bloques de un jugador. Hace falta terreno transitable y espacio para el grupo completo, dentro de chunks cargados.

El límite global natural es el menor entre el techo de la etapa y su límite por
jugador multiplicado por los jugadores elegibles. También se aplica el límite
por jugador en una caja de 96 bloques alrededor. Se cuentan los troopers cargados;
estos límites son propios del mod e independientes del cupo de otros monstruos.
Si no cabe el grupo completo o se alcanzó un límite, se omite esa oportunidad.

Los límites configurados actúan como techos adicionales: bajarlos reduce la
población de cualquier etapa. El intervalo base se multiplica siempre por 1,5
(redondeado hacia abajo y con mínimo de 5 segundos), dando 15 segundos con la
configuración inicial. La probabilidad base se multiplica por 0,40, 0,50, 0,60,
0,70 o 0,80 según la etapa. Las búsquedas se limitan a 1, 1, 2, 2 o 2,
sin superar `patrolsPerCycle`. La progresión también se aplica a configuraciones
antiguas. `spawnChance=0` sigue impidiendo nuevas apariciones automáticas.

Los cambios requieren **reiniciar el juego o servidor**. En multijugador manda la configuración del servidor. Pacífico, `doMobSpawning=false` y `spawn-monsters=false` bloquean las apariciones automáticas. No aparecen naturalmente en el Nether ni en el End. Conservan las reglas de desaparición de los mobs hostiles y no mantienen chunks cargados.

`laserDamage` controla el daño de los troopers; `playerLaserDamage` controla el del
jugador. Al actualizar una configuración antigua sin `balanceVersion`, los valores
anteriores de 3 y 10 se actualizan a 3,5 y 8, respectivamente. Se conservan los
valores personalizados y se guarda la versión de balance para no migrarlos otra vez.

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
por disparo. Se rompe al agotar sus 32 usos; los golpes cuerpo a cuerpo también
gastan uno. Creativo no consume durabilidad. No tiene receta: se consigue con un
15 % de probabilidad al morir un trooper, con la durabilidad completa. Por una
baja del jugador hay un 15 % de casco, un 15 % de bláster y un 70 % sin ninguno.
Nunca entrega ambos objetos. Saqueo no aumenta las probabilidades y se respeta `doMobLoot`.

## Destructor imperial

Nueva entidad `tropercraft-mod:star_destroyer`, inspirada en la nave de referencia:
unos **145 bloques de largo**, 96 de ancho, casco biselado con paneles de blindaje,
puente escalonado, dos cúpulas de sensores y siete motores luminosos con toberas.
La parte inferior incorpora un hangar con profundidad, vigas y luces de guía. Es pasiva, no dispara ni rompe bloques y no tiene colisión física.
Vuela a **0,5 bloques por segundo**, a unos **48 bloques sobre el terreno** para
verse desde el suelo, sin una altura Y mínima fija. Comprueba el terreno bajo
el casco y delante de la proa en chunks cargados; sube al encontrar zonas altas
y vuelve a descender suavemente después de las montañas. Las naves guardadas
con la altura anterior recalculan su vuelo al cargar el mundo.

El Overworld comprueba cada 30 segundos si puede iniciar un sobrevuelo. Se crea
como máximo una nave automática cargada; cada vuelo dura cinco minutos. No fuerza
la carga de chunks. El motor es un bucle original de 12 segundos con graves,
vibración y maquinaria, audible hasta **320 bloques**, con volumen según distancia.
Su volumen usa la categoría Ambiente del juego.

La primera patrulla de **3 troopers** se despliega en tierra bajo la nave sin
sorteo ni bloqueo por cupos. Si no hay sitio seguro, reintenta cada 5 segundos.
Después intenta refuerzos cada 30 segundos, con un máximo local de 6–12 troopers
según la etapa y respetando el límite global configurado. No aparece tropa en
Pacífico, con aparición de mobs desactivada o `spawnChance=0`.

Para crear una nave de prueba en una zona cargada del Overworld usa
`/stardestroyer`. También existe `/summon tropercraft-mod:star_destroyer`.
El modelo se regenera con `python3 tools/generate_star_destroyer_model.py` y el
motor con `python3 tools/generate_ship_engine.py` (requiere ffmpeg).

## Cabeza de trooper y disfraz

Se fabrica en una mesa de crafteo con **5 lingotes de hierro y 1 carbón mineral**:

```text
Hierro   Hierro   Hierro
Hierro   Carbón   Hierro
Vacío    Vacío    Vacío
```

También hay un **15 % de probabilidad por baja atribuida a un jugador** de que un stormtrooper suelte una cabeza. Si suelta la cabeza, no suelta el bláster. Saqueo no aumenta ese porcentaje y las muertes sin jugador no sueltan cabezas. El botín respeta `doMobLoot`.

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

El JAR instalable queda en `build/libs/tropercraft-mod-1.0.4.jar`. Las pruebas están en `src/gametest/`, usan un mundo aislado en `build/gametest/` y escriben `build/reports/gametest.xml`. El mod de pruebas no se incluye en el JAR distribuible. GitHub Actions ejecuta la compilación y los GameTests.

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

## Versión 1.0.5 — nave hostil

La nave tiene una hitbox que cubre todo el modelo y 100 puntos de vida, como un ravager. Desde la tercera noche (tick 61000), llega frente al jugador con una animación de salida del hiperespacio de 1,5 segundos, lo persigue y dispara un láser de 12 puntos de daño cada 3 segundos, con carga previa y línea de visión. No destruye bloques. El encuentro natural se guarda una vez por jugador y mundo; jugadores pendientes pueden encontrarla en noches posteriores. Respeta Pacífico y doMobSpawning.

El audio solicitado de YouTube sigue a la nave en mono, con volumen máximo de 0,8, normalización a -18 LUFS, pico limitado y reducción gradual hasta silenciarse a 192 bloques. Se controla con el volumen de criaturas hostiles. `/stardestroyer` permite probar la nave manualmente.

## Versión 1.0.6 — evasión, búsqueda y láseres explosivos

La nave parpadea en rojo y emite un golpe metálico al recibir daño. Su velocidad máxima baja a 2 bloques por segundo. Puedes perderla al alejarte más de 160 bloques en horizontal, bajar cuatro bloques desde la altura de la persecución o esconderte de su vista durante dos segundos. Patrulla tu última posición conocida durante 30 segundos y vuelve frente a ti desde el hiperespacio, conservando el daño recibido. El temporizador sigue funcionando aunque la zona de la nave se descargue; morir cancela el regreso, incluso si reapareces antes de que termine la espera.

También ataca a otros mobs. Sus láseres explotan contra los bloques (potencia 1,5, sin fuego); respetan mobGriefing y protegen a troopers y otras naves del daño y el empuje de la explosión. Al aparecer y al volver a buscarte envía al chat: «[TRANSMISIÓN IMPERIAL] Un Destructor Estelar ha fijado tu posición. ¡Va a por ti!».

## Versión 1.0.7 — combate, retirada y una sola nave

La sirena solo suena durante el combate; al patrullar, buscar, llegar o retirarse suenan los motores. Entrar de nuevo en alcance y a la vista reactiva el ataque inmediatamente. Llevar la cabeza de trooper en la ranura del casco protege al jugador de los disparos y explosiones de la nave. Los demás mobs visibles dentro del alcance siguen siendo objetivos; los troopers son aliados.

Al morir el jugador perseguido, o al matar la nave a un jugador con sus láseres, el destructor deja de atacar, se estira y acelera hacia el hiperespacio durante 1,5 segundos y desaparece. Puede regresar en la noche siguiente. Destruirlo bloquea todas sus apariciones hasta la siguiente noche, incluidas las provocadas y el comando. Matar más de cinco troopers (la sexta baja atribuida al jugador) también llama a la nave, incluso de día, respetando ese bloqueo. El contador se guarda y se reinicia cuando responde la nave.

Solo puede existir una nave: los encuentros nocturnos, las bajas, `/stardestroyer`, las invocaciones directas y la carga de chunks comparten una reserva persistente. Descargar la zona de la nave no permite crear otra; su regreso sustituye la copia anterior.

## Versión 1.0.8 — focus al jugador y alarma imperial

La nave prioriza siempre al jugador visible, dentro del alcance y sin casco de trooper, aunque haya otros mobs más cerca. Si no puede atacar al jugador, sigue disparando a otros mobs visibles salvo troopers. La sirena solo se activa al ver a un jugador válido; combatir contra otros mobs mantiene el sonido de motores. Al perder de vista al jugador la sirena se apaga inmediatamente.

La alarma alerta una vez por segundo a todos los troopers cargados en un radio de 192 bloques alrededor de la nave. Estos priorizan al jugador señalado, lo persiguen mediante navegación y conservan la alerta durante diez segundos desde el último aviso, aunque estuvieran fuera de su alcance habitual. El casco de trooper también cancela esta persecución.

## Versión 1.0.9 — escudos, sensores y baterías dobles

Todos los láseres (nave, troopers y jugador) se bloquean de frente con un escudo preparado. Conservan el funcionamiento normal del escudo, pero gastan al menos 16 puntos de durabilidad por impacto, y más en los disparos potentes. Los láseres de la nave mantienen su alcance durante el vuelo y se disparan en dos parejas paralelas separadas 0,3 segundos: cuatro proyectiles por ciclo.

Los sensores cubren 320 bloques delante, 192 debajo del casco, 128 hacia los lados y 96 detrás; respetan la línea de visión. El jugador sigue siendo prioritario. Los giros aceleran poco a poco hasta 4,4 grados por segundo para transmitir el peso de la nave.

## Versión 1.1.0 — persecución autónoma e identificación

La nave adquiere por sí sola al jugador en Supervivencia, sin esperar una agresión, y se orienta para perseguirlo aunque todavía no tenga línea de tiro. Los sensores se amplían a 512 bloques delante, 320 debajo y 256 a ambos lados y detrás, en zonas cargadas. Se mantienen el focus al jugador, la sirena solo al verlo, el escudo y las dos ráfagas dobles.

Acertar a la nave llevando un casco de trooper descubre el disfraz ante la nave y los troopers hasta la siguiente muerte. La identificación se guarda y no desaparece al cambiar de casco, reconectarse o reemplazarse la nave. Un láser recuerda si se disparó con el casco puesto.

Nave y troopers atacan a mobs terrestres y ahogados; ignoran a voladores, criaturas acuáticas y aliados. Sus proyectiles, y las explosiones de la nave, también respetan estas exclusiones. Al terminar la animación de hiperespacio se elimina la entidad y se retira su identificador para que una copia guardada no pueda reaparecer al cargar chunks.

## Equipo inicial añadido a la versión 1.1.0

Al entrar por primera vez en cada mundo recibes una DL-44 de supervivencia, casco, pechera, pantalones y botas de explorador recuperados, y un libro de cinco páginas con un informe de contingencia de la Alianza Rebelde. La armadura se equipa en las ranuras vacías; si llevas otra pieza, la nueva va al inventario. No se sobrescribe equipo existente y un inventario lleno deja los objetos junto al jugador. La entrega se guarda por UUID y mundo: no se repite al morir, reconectarte o cargar la partida.

La pistola usa un modelo 3D nativo basado en la referencia aportada, con mira telescópica y empuñadura de madera. Hace 3,5 puntos de daño: seis impactos eliminan a un stormtrooper sano. Su cooldown es un 20 % menor que el de los troopers (24 frente a 30 ticks por defecto), tiene 250 usos y sus disparos conservan el bloqueo con escudo y las reglas de identificación. El bláster imperial mantiene su daño anterior.

La armadura olive y mecánica adapta la segunda referencia a un modelo articulado para Minecraft; tiene exactamente la durabilidad y protección de la cota de malla y no sirve como disfraz imperial. La DL-44 está basada en el modelo canónico; la calibración, esta armadura recuperada y el informe de misión son adaptaciones originales del mod.
