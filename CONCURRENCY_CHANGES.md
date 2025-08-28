# Documentacion de Cambios de Concurrencia

## Resumen
Este documento justifica cada modificacion realizada para mejorar la concurrencia y eliminar problemas thread-safety en el juego Snake Race.

---

## 1. Eliminacion de Esperas Activas en SnakeRunner
**Prioridad: ALTA**

### Riesgo Original:
- Thread.sleep() causaba esperas activas desperdiciando CPU
- Falta de coordinacion con GameClock generaba movimientos desincronizados
- Control de pause/resume inconsistente entre serpientes

### Solucion Implementada:
- Reemplazado Thread.sleep() por mecanismo wait/notify
- Implementada interfaz GameClockListener para coordinacion
- Agregados metodos pause(), resume(), stop() con sincronizacion

### Por que esta solucion:
- wait/notify libera CPU durante esperas (mas eficiente)
- Coordinacion centralizada con GameClock evita desincronizacion
- Control unificado de estado permite pause/resume instantaneo
- Sistema hibrido con fallback timing para robustez

---

## 2. Colecciones Thread-Safe en Board
**Prioridad: CRITICA**

### Riesgo Original:
- HashSet<Position> mice causaba ConcurrentModificationException
- HashSet<Position> obstacles generaba inconsistencias en lecturas
- HashSet<Position> turbo tenia accesos no atomicos
- HashMap<Position, Position> teleports vulnerable a corrupcion

### Solucion Implementada:
- HashSet → ConcurrentHashMap.newKeySet() para mice, obstacles, turbo
- HashMap → ConcurrentHashMap para teleports
- Eliminados synchronized en metodos de acceso (mice(), obstacles(), etc.)

### Por que esta solucion:
- Thread-safe sin locks explicitos mejora rendimiento
- Operaciones atomicas previenen ConcurrentModificationException
- Accesos concurrentes seguros sin bloquear otros hilos
- Mejor escalabilidad con mayor numero de serpientes

---

## 3. Sincronizacion Granular en Board.step()
**Prioridad: MEDIA**

### Riesgo Original:
- Metodo synchronized step() bloqueaba todo el tablero
- Una serpiente bloqueaba movimiento de todas las demas
- Rendimiento limitado en escenarios multi-serpiente

### Solucion Implementada:
- Dividido synchronized step() en regiones criticas especificas
- Agregados ReentrantLock miceLock y itemGenerationLock
- Operaciones de solo lectura sin sincronizacion
- Locks granulares solo para operaciones que requieren atomicidad

### Por que esta solucion:
- Multiples serpientes pueden moverse simultaneamente cuando es seguro
- Locks especificos reducen contention y mejoran throughput
- Lecturas concurrentes de obstaculos y teleports sin bloqueos
- Mejor utilizacion de CPU en sistemas multi-core

---

## 4. Lista Thread-Safe en SnakeApp
**Prioridad: BAJA**

### Riesgo Original:
- ArrayList<Snake> vulnerable a ConcurrentModificationException
- Accesos concurrentes entre UI thread e initialization thread
- ArrayList<SnakeRunner> sin proteccion contra iteracion concurrente

### Solucion Implementada:
- ArrayList → CopyOnWriteArrayList para snakes y snakeRunners
- Eliminada sincronizacion explicita en togglePause()

### Por que esta solucion:
- CopyOnWriteArrayList optimizada para lecturas frecuentes (UI updates)
- Thread-safe sin locks explicitos para mejor rendimiento UI
- Iteradores no lanzan ConcurrentModificationException
- Escrituras atomicas durante inicializacion

---

## 5. Coordinacion GameClock-Serpientes
**Prioridad: ALTA**

### Riesgo Original:
- Timing independiente entre GameClock y serpientes
- Pause/resume descoordinado causaba estados inconsistentes
- Falta de sincronizacion entre UI updates y snake movement

### Solucion Implementada:
- Agregada interfaz GameClockListener con metodos onTick(), onPause(), onResume()
- GameClock notifica automaticamente a todos los listeners registrados
- Sistema de registro/desregistro de listeners thread-safe

### Por que esta solucion:
- Coordinacion centralizada elimina estados inconsistentes
- Un solo comando controla todo el juego (pause/resume)
- Escalabilidad para cualquier numero de serpientes
- Separacion clara entre timing UI y timing game logic

---

## Beneficios Generales Obtenidos

### Rendimiento:
- Eliminacion de esperas activas reduce uso innecesario de CPU
- Locks granulares permiten mayor paralelismo
- CopyOnWriteArrayList optimiza lecturas frecuentes de UI

### Estabilidad:
- Eliminadas las ConcurrentModificationException
- Estados consistentes entre todos los componentes
- Manejo robusto de pause/resume/stop

### Escalabilidad:
- Sistema funciona eficientemente con 10+ serpientes
- Arquitectura preparada para mayor concurrencia
- Locks especificos minimizan contention

### Mantenibilidad:
- Separacion clara de responsabilidades
- Interfaces bien definidas entre componentes
- Documentacion de cada decision de diseno

---

## Orden de Implementacion
1. SnakeRunner wait/notify (elimina esperas activas)
2. Board colecciones thread-safe (elimina crashes)
3. Board sincronizacion granular (mejora rendimiento)
4. SnakeApp listas thread-safe (protege UI)
5. GameClock coordinacion (unifica control)

Este orden priorizo eliminar crashes criticos primero, luego optimizar rendimiento.
