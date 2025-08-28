package co.eci.snake.core.engine;

import co.eci.snake.core.GameState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public final class GameClock implements AutoCloseable {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final java.util.concurrent.atomic.AtomicReference<GameState> state = new AtomicReference<>(GameState.STOPPED);
  private final List<GameClockListener> listeners = new CopyOnWriteArrayList<>();

  public GameClock(long periodMillis, Runnable tick) {
    if (periodMillis <= 0)
      throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = java.util.Objects.requireNonNull(tick, "tick");
  }

  public void start() {
    if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
      scheduler.scheduleAtFixedRate(() -> {
        if (state.get() == GameState.RUNNING) {
          tick.run();
          notifyListeners();
        }
      }, 0, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  public void pause() {
    state.set(GameState.PAUSED);
    listeners.forEach(GameClockListener::onPause);
  }

  public void resume() {
    state.set(GameState.RUNNING);
    listeners.forEach(GameClockListener::onResume);
  }

  public void stop() {
    state.set(GameState.STOPPED);
    listeners.forEach(GameClockListener::onStop);
  }

  public void addListener(GameClockListener listener) {
    listeners.add(listener);
  }

  public void removeListener(GameClockListener listener) {
    listeners.remove(listener);
  }

  private void notifyListeners() {
    listeners.forEach(GameClockListener::onTick);
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }

  @FunctionalInterface
  public interface GameClockListener {
    void onTick();

    default void onPause() {
    }

    default void onResume() {
    }

    default void onStop() {
    }
  }
}
