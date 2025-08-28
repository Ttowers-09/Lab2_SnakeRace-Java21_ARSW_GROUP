package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import java.util.concurrent.ThreadLocalRandom;

/**
 * SnakeRunner - Ejecuta el movimiento de una serpiente de forma concurrente
 * 
 * CAMBIOS DE CONCURRENCIA:
 * - Eliminado Thread.sleep() (espera activa) -> wait/notify (espera eficiente)
 * - Implementa GameClockListener para coordinacion con GameClock
 * - Agregados pause/resume/stop thread-safe
 * - Sistema hibrido: coordinacion por clock + fallback timing
 */
public final class SnakeRunner implements Runnable, GameClock.GameClockListener {
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;

  // Sincronizacion para wait/notify en lugar de Thread.sleep()
  private final Object pauseLock = new Object();
  private volatile boolean paused = false;
  private volatile boolean stopped = false;
  private volatile boolean clockTick = false;

  private long lastMoveTime = 0;

  public SnakeRunner(Snake snake, Board board) {
    this.snake = snake;
    this.board = board;
  }

  @Override
  public void run() {
    try {
      lastMoveTime = System.currentTimeMillis();
      while (!Thread.currentThread().isInterrupted() && !stopped) {
        synchronized (pauseLock) {
          while (paused && !stopped) {
            pauseLock.wait();
          }
        }

        if (stopped)
          break;

        synchronized (pauseLock) {
          while (!clockTick && !stopped) {
            int sleepTime = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
            pauseLock.wait(sleepTime);

            if (!clockTick) {
              long currentTime = System.currentTimeMillis();
              if (currentTime - lastMoveTime >= sleepTime) {
                break;
              }
            }
          }
          clockTick = false; // Reset clock tick
        }

        if (stopped)
          break;

        lastMoveTime = System.currentTimeMillis();

        maybeTurn();
        var res = board.step(snake);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        }
        if (turboTicks > 0)
          turboTicks--;
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p)
      randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }

  /**
   * Pauses the snake runner using wait/notify mechanism
   */
  public void pause() {
    synchronized (pauseLock) {
      paused = true;
    }
  }

  /**
   * Resumes the snake runner using wait/notify mechanism
   */
  public void resume() {
    synchronized (pauseLock) {
      paused = false;
      pauseLock.notifyAll();
    }
  }

  /**
   * Stops the snake runner
   */
  public void stop() {
    synchronized (pauseLock) {
      stopped = true;
      pauseLock.notifyAll();
    }
  }

  /**
   * Triggers an immediate move by notifying waiting threads
   */
  public void tick() {
    synchronized (pauseLock) {
      if (!paused && !stopped) {
        pauseLock.notify();
      }
    }
  }

  @Override
  public void onTick() {
    synchronized (pauseLock) {
      clockTick = true;
      pauseLock.notify();
    }
  }

  @Override
  public void onPause() {
    pause();
  }

  @Override
  public void onResume() {
    resume();
  }

  @Override
  public void onStop() {
    stop();
  }
}
