package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.concurrent.ThreadLocalRandom;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  
  private final Object pauseLock = new Object();
  private volatile boolean paused = false;
  private volatile boolean stopped = false;
  
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
        
        if (stopped) break;
        
        int sleepTime = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - lastMoveTime;
        
        synchronized (pauseLock) {
          if (timeElapsed < sleepTime && !stopped) {
            long waitTime = sleepTime - timeElapsed;
            pauseLock.wait(waitTime);
          }
        }
        
        if (stopped) break;
        
        lastMoveTime = System.currentTimeMillis();
        
        maybeTurn();
        var res = board.step(snake);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        }
        if (turboTicks > 0) turboTicks--;
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
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
}
