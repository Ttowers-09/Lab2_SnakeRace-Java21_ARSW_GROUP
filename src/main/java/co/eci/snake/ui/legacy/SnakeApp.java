package co.eci.snake.ui.legacy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;
public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final JButton exitButton;
  private final GameClock clock;
  private long startTime = 0;
  private long pausedTime = 0;
  private long totalPaused = 0;
  private final java.util.List<Snake> snakes = new CopyOnWriteArrayList<>();
  private final java.util.List<SnakeRunner> snakeRunners = new CopyOnWriteArrayList<>();
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private volatile String pauseStats = "";
  private boolean started = false;

  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }
    this.gamePanel = new GamePanel(board,
      () -> snakes,
      () -> paused.get(),
      () -> pauseStats,
      () -> {
        if (startTime == 0) return "Tiempo: 00:00";
        long elapsed;
        if (paused.get()) {
          elapsed = pausedTime - startTime - totalPaused;
        } else {
          elapsed = System.currentTimeMillis() - startTime - totalPaused;
        }
        int secs = (int)(elapsed / 1000);
        int mins = secs / 60;
        secs = secs % 60;
        return String.format("Tiempo: %02d:%02d", mins, secs);
      }
    );
  this.actionButton = new JButton("Iniciar");
  this.exitButton = new JButton("Salir");

  setLayout(new BorderLayout());
  add(gamePanel, BorderLayout.CENTER);
  // Panel para los botones en la parte inferior
  var buttonPanel = new javax.swing.JPanel();
  buttonPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
  buttonPanel.add(actionButton);
  buttonPanel.add(exitButton);
  add(buttonPanel, BorderLayout.SOUTH);
  exitButton.addActionListener(e -> System.exit(0));

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    var exec = Executors.newVirtualThreadPerTaskExecutor();
    for (Snake snake : snakes) {
      SnakeRunner runner = new SnakeRunner(snake, board);
      snakeRunners.add(runner);
      clock.addListener(runner);
      exec.submit(runner);
    }

    actionButton.addActionListener((ActionEvent e) -> togglePause());

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    var player = snakes.get(0);
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"), "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"), "up");
    im.put(KeyStroke.getKeyStroke("DOWN"), "down");
    am.put("left", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.LEFT);
      }
    });
    am.put("right", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.RIGHT);
      }
    });
    am.put("up", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.UP);
      }
    });
    am.put("down", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.DOWN);
      }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke("A"), "p2-left");
      im.put(KeyStroke.getKeyStroke("D"), "p2-right");
      im.put(KeyStroke.getKeyStroke("W"), "p2-up");
      im.put(KeyStroke.getKeyStroke("S"), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.LEFT);
        }
      });
      am.put("p2-right", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.RIGHT);
        }
      });
      am.put("p2-up", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.UP);
        }
      });
      am.put("p2-down", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.DOWN);
        }
      });
    }

    setVisible(true);
    // No iniciar el clock aún, solo al presionar "Iniciar"
    SwingUtilities.invokeLater(() -> {
      gamePanel.requestFocusInWindow();
    });
  }

  private void togglePause() {
  if (!started) {
    clock.start();
    startTime = System.currentTimeMillis();
    actionButton.setText("Pausar");
    started = true;
    paused.set(false);
    pauseStats = "";
  } else if (!paused.get()) {
    actionButton.setText("Reanudar");
    clock.pause();
    pausedTime = System.currentTimeMillis();
    paused.set(true);
    // Calcular estadística en pausa
    Snake longest = snakes.stream()
        .max(Comparator.comparingInt(Snake::length))
        .orElse(null);
    if (longest != null) {
      pauseStats = "Serpiente más larga: #" + longest.id() +
             " con " + longest.length() + " segmentos";
    } else {
      pauseStats = "No hay serpientes";
    }
  } else {
    actionButton.setText("Pausar");
    clock.resume();
    if (pausedTime > 0) totalPaused += System.currentTimeMillis() - pausedTime;
    paused.set(false);
    pauseStats = "";
  }
  gamePanel.repaint();
  SwingUtilities.invokeLater(() -> gamePanel.requestFocusInWindow());
}


  public static final class GamePanel extends JPanel {
  private final Board board;
  private final Supplier snakesSupplier;
  private final java.util.function.Supplier<Boolean> pausedSupplier;
  private final java.util.function.Supplier<String> statsSupplier;
  private final java.util.function.Supplier<String> clockSupplier;
  private final int cell = 20;

  @FunctionalInterface
  public interface Supplier {
    List<Snake> get();
  }

    public GamePanel(Board board, Supplier snakesSupplier,
                   java.util.function.Supplier<Boolean> pausedSupplier,
                   java.util.function.Supplier<String> statsSupplier,
                   java.util.function.Supplier<String> clockSupplier) {
    this.board = board;
    this.snakesSupplier = snakesSupplier;
    this.pausedSupplier = pausedSupplier;
    this.statsSupplier = statsSupplier;
    this.clockSupplier = clockSupplier;
    setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
    setBackground(Color.WHITE);
    setFocusable(true);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    var g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }
      var snakes = snakesSupplier.get();
      int idx = 0;
      for (Snake s : snakes) {
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
        idx++;
      }
  // Reloj en la esquina superior derecha
  g2.setColor(Color.BLACK);
  g2.drawString(clockSupplier.get(), getWidth() - 110, 30);

      if (pausedSupplier.get()) {
        var snakesList = snakesSupplier.get();
        var ranking = snakesList.stream()
            .sorted(Comparator.comparingInt(Snake::length).reversed())
            .toList();
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(getWidth()/2 - 150, getHeight()/2 - 80, 300, 150, 20, 20);
        g2.setColor(Color.WHITE);
        g2.drawString("PAUSA", getWidth()/2 - 20, getHeight()/2 - 40);
        int y = getHeight()/2;
        if (ranking.size() == 1) {
            var s = ranking.get(0);
            int idxSnake = snakesList.indexOf(s);
            String nombre = (idxSnake == 0) ? "Jugador Verde" : "Jugador Azul";
            g2.drawString(nombre + " - Longitud: " + s.length(), getWidth()/2 - 60, y);
        } else {
            var mejor = ranking.get(0);
            int idxMejor = snakesList.indexOf(mejor);
            String nombreMejor = (idxMejor == 0) ? "Jugador Verde" : "Jugador Azul";
            g2.drawString("Mejor: " + nombreMejor + " (" + mejor.length() + ")", getWidth()/2 - 80, y);
            y += 20;
            var peor = ranking.get(ranking.size()-1);
            int idxPeor = snakesList.indexOf(peor);
            String nombrePeor = (idxPeor == 0) ? "Jugador Verde" : "Jugador Azul";
            g2.drawString("Peor: " + nombrePeor + " (" + peor.length() + ")", getWidth()/2 - 80, y);
        }
      }


      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
