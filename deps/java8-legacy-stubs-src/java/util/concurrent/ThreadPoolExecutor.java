package java.util.concurrent;

public class ThreadPoolExecutor implements ExecutorService {
  public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {}
  public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {}
  public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {}
  public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {}
  public int getCorePoolSize() { return 0; }
  public void setCorePoolSize(int corePoolSize) {}
  public void allowCoreThreadTimeOut(boolean value) {}
  public BlockingQueue<Runnable> getQueue() { return null; }
  public void execute(Runnable command) {}
  public void shutdown() {}
  public java.util.List<Runnable> shutdownNow() { return null; }
  public boolean isShutdown() { return false; }
  public boolean isTerminated() { return false; }
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return false; }
  public <T> Future<T> submit(Callable<T> task) { return null; }
  public <T> Future<T> submit(Runnable task, T result) { return null; }
  public Future<?> submit(Runnable task) { return null; }
  public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException { return null; }
  public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException { return null; }
  public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException { return null; }
  public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return null; }
  public static class CallerRunsPolicy implements RejectedExecutionHandler {
    public CallerRunsPolicy() {}
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {}
  }
}
