package com.claro.runtime_utilities.http;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncHttpServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Function;

abstract class $ClaroLauncher {
  protected final Eventloop eventloop;
  public final AsyncHttpServer server;
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);
  private final CountDownLatch completeLatch = new CountDownLatch(1);

  $ClaroLauncher(Eventloop eventloop, Function<Eventloop, AsyncHttpServer> serverConstructor) {
    this.eventloop = eventloop;
    this.server = serverConstructor.apply(this.eventloop);
  }

  public final void launch() throws Exception {
    try {
      // Start things in dependency order.
      startEventLoop();
      startServer();

      run();

      // Stop things in reverse dependency order.
      stopServer();
      stopEventLoop();
    } catch (Exception e) {
      throw e;
    } catch (Throwable e) {
      System.out.printf("JVM Fatal Error:\n%s\n", e);
      System.exit(-1);
    } finally {
      completeLatch.countDown();
    }
  }

  private void startEventLoop() {
    Executors.defaultThreadFactory().newThread(() -> {
      eventloop.keepAlive(true);
      eventloop.run();
    }).start();
  }

  private void stopEventLoop() {
    Thread eventloopThread = eventloop.getEventloopThread();
    if (eventloopThread == null) {
      // already stopped
      return;
    }
    eventloop.execute(() -> {
      eventloop.keepAlive(false);
    });
    try {
      eventloopThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void startServer() {
    this.eventloop.execute(() -> {
      try {
        this.server.listen();
      } catch (Exception e) {
        throw new RuntimeException("Failed to start ActiveJ AsyncHttpServer", e);
      }
    });
  }

  private void stopServer() {
    this.eventloop.execute(() -> this.server.close().whenException(
        e -> {
          throw new RuntimeException("Exception while stopping ActiveJ AsyncHttpServer", e);
        }
    ));
  }

  /**
   * Launcher's main method.
   */
  protected abstract void run() throws Exception;

  private final Thread shutdownHook = new Thread(() -> {
    try {
      // release anything blocked at `awaitShutdown` call
      shutdownLatch.countDown();
      // then wait for the `launch` call to finish
      completeLatch.await();
      // and wait a bit for things after the `launch` call, such as JUnit finishing or whatever
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }, "$ClaroShutdownNotification");

  /**
   * Blocks the current thread until shutdown notification releases it.
   * <br>
   * Shutdown notification is released on JVM shutdown or by calling {@link $ClaroLauncher#shutdown()}
   */
  protected final void awaitShutdown() throws InterruptedException {
    // check if shutdown is not in process already
    if (shutdownLatch.getCount() != 1) {
      return;
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    shutdownLatch.await();
  }

  /**
   * Manually releases all threads waiting for shutdown.
   *
   * @see $ClaroLauncher#awaitShutdown()
   */
  public final void shutdown() {
    Runtime.getRuntime().removeShutdownHook(shutdownHook);
    shutdownLatch.countDown();
  }
}
