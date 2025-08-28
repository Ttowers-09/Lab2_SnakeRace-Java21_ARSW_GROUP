package co.eci.snake.core;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Board - Tablero de juego con soporte completo para concurrencia
 * 
 * CAMBIOS DE CONCURRENCIA:
 * - HashSet -> ConcurrentHashMap.newKeySet() (elimina ConcurrentModificationException)
 * - HashMap -> ConcurrentHashMap (accesos thread-safe)
 * - synchronized step() -> locks granulares (mejor rendimiento)
 * - Multiples serpientes pueden moverse simultaneamente cuando es seguro
 */
public final class Board {
  private final int width;
  private final int height;

  // Colecciones thread-safe para prevenir ConcurrentModificationException
  private final Set<Position> mice = ConcurrentHashMap.newKeySet();
  private final Set<Position> obstacles = ConcurrentHashMap.newKeySet();
  private final Set<Position> turbo = ConcurrentHashMap.newKeySet();
  private final Map<Position, Position> teleports = new ConcurrentHashMap<>();

  // Locks granulares para operaciones especificas (mejor que synchronized general)
  private final ReentrantLock miceLock = new ReentrantLock();
  private final ReentrantLock itemGenerationLock = new ReentrantLock();

  public enum MoveResult { MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED }

  public Board(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    this.width = width;
    this.height = height;
    for (int i=0;i<6;i++) mice.add(randomEmpty());
    for (int i=0;i<4;i++) obstacles.add(randomEmpty());
    for (int i=0;i<3;i++) turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  public int width() { return width; }
  public int height() { return height; }

  public Set<Position> mice() { return mice; }
  public Set<Position> obstacles() { return obstacles; }
  public Set<Position> turbo() { return turbo; }
  public Map<Position, Position> teleports() { return teleports; }

  public MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;

    boolean teleported = false;
    if (teleports.containsKey(next)) {
      next = teleports.get(next);
      teleported = true;
    }

    boolean ateMouse = false;
    boolean ateTurbo = turbo.remove(next);

    miceLock.lock();
    try {
      ateMouse = mice.remove(next);
      snake.advance(next, ateMouse);
    } finally {
      miceLock.unlock();
    }

    if (ateMouse) {
      itemGenerationLock.lock();
      try {
        mice.add(randomEmpty());
        obstacles.add(randomEmpty());
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
          turbo.add(randomEmpty());
        }
      } finally {
        itemGenerationLock.unlock();
      }
    }

    if (ateTurbo) return MoveResult.ATE_TURBO;
    if (ateMouse) return MoveResult.ATE_MOUSE;
    if (teleported) return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  private void createTeleportPairs(int pairs) {
    itemGenerationLock.lock();
    try {
      for (int i=0;i<pairs;i++) {
        Position a = randomEmpty();
        Position b = randomEmpty();
        teleports.put(a, b);
        teleports.put(b, a);
      }
    } finally {
      itemGenerationLock.unlock();
    }
  }

  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width*height*2) break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }
}
