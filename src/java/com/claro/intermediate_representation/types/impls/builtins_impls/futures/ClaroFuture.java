package com.claro.intermediate_representation.types.impls.builtins_impls.futures;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClaroFuture<T> implements ClaroBuiltinTypeImplementation, ListenableFuture<T> {

  private final Type claroType;
  private final ListenableFuture<T> defer;

  public ClaroFuture(Type wrappedClaroType, ListenableFuture<T> defer) {
    this.claroType = Types.FutureType.wrapping(wrappedClaroType);
    this.defer = defer;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return defer.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return defer.isCancelled();
  }

  @Override
  public boolean isDone() {
    return defer.isDone();
  }

  @Override
  public T get() {
    try {
      return defer.get();
    } catch (Exception cause) {
      // We'll simply panic for any Exception thrown while processing a future because there is no non-Panic Exception
      // type supported in Claro.
      throw new Panic(cause);
    }
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    try {
      return defer.get(timeout, unit);
    } catch (Exception cause) {
      // We'll simply panic for any Exception thrown while processing a future because there is no non-Panic Exception
      // type supported in Claro.
      throw new Panic(cause);
    }
  }

  @Override
  public Type getClaroType() {
    return claroType;
  }

  @Override
  public String toString() {
    return this.claroType.toString();
  }

  @Override
  public void addListener(Runnable runnable, Executor executor) {
    defer.addListener(runnable, executor);
  }

  // TODO(steving) Come back to this and model "Panics" as a top level construct in Claro not just for futures.
  //  Claro will handle Errors similarly to the way that Rust handles errors so I'm thinking that "throwing an
  //  Exception" should be reserved for situations that most programmers should not be handling. Maybe framework code
  //  should catch things like exceptions to prevent whole servers from crashing due to one recoverable error for
  //  example.
  public static class Panic extends RuntimeException {
    public Panic(Exception cause) {
      super("Panic! Unexpected error occurred while resolving a future<>.", cause);
    }
  }
}
