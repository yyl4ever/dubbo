/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.threadpool;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The most important difference between this Executor and other normal Executor is that this one doesn't manage
 * any thread.
 *
 * Tasks submitted to this executor through {@link #execute(Runnable)} will not get scheduled to a specific thread, though normal executors always do the schedule.
 * Those tasks are stored in a blocking queue and will only be executed when a thread calls {@link #waitAndDrain()}, the thread executing the task
 * is exactly the same as the one calling waitAndDrain.
 * ThreadlessExecutor 是一种特殊类型的线程池，与其他正常的线程池最主要的区别是：ThreadlessExecutor 内部不管理任何线程。
 *
 * **那为什么会有 ThreadlessExecutor 这个实现呢？**这主要是因为在 Dubbo 2.7.5 版本之前，在 WrappedChannelHandler 中会为每个连接启动一个线程池。
 * 老版本中没有 ExecutorRepository 的概念，不会根据 URL 复用同一个线程池，而是通过 SPI 找到 ThreadPool 实现创建新线程池。
 */
public class ThreadlessExecutor extends AbstractExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(ThreadlessExecutor.class.getName());
    //阻塞队列，用来在 IO 线程和业务线程之间传递任务。
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    /**
     * ThreadlessExecutor 底层关联的共享线程池，当业务线程已经不再等待响应时，会由该共享线程执行提交的任务。
     */
    private ExecutorService sharedExecutor;
    /**
     * 指向请求对应的 DefaultFuture 对象
     */
    private CompletableFuture<?> waitingFuture;
    /**
     * ThreadlessExecutor 中的 waitAndDrain() 方法一般与一次 RPC 调用绑定，只会执行一次。
     * 当后续再次调用 waitAndDrain() 方法时，会检查 finished 字段，若为true，则此次调用直接返回。
     * 当后续再次调用 execute() 方法提交任务时，会根据 waiting 字段决定任务是放入 queue 队列等待业务线程执行，
     * 还是直接由 sharedExecutor 线程池执行。
     */
    private boolean finished = false;

    private volatile boolean waiting = true;

    private final Object lock = new Object();

    public ThreadlessExecutor(ExecutorService sharedExecutor) {
        this.sharedExecutor = sharedExecutor;
    }

    public CompletableFuture<?> getWaitingFuture() {
        return waitingFuture;
    }

    public void setWaitingFuture(CompletableFuture<?> waitingFuture) {
        this.waitingFuture = waitingFuture;
    }

    public boolean isWaiting() {
        return waiting;
    }

    /**
     * Waits until there is a task, executes the task and all queued tasks (if there're any). The task is either a normal
     * response or a timeout response.
     * 首先会检测 finished 字段值，然后获取阻塞队列中的全部任务并执行，执行完成之后会修改finished和 waiting 字段，标识当前 ThreadlessExecutor 已使用完毕，无业务线程等待。
     */
    public void waitAndDrain() throws InterruptedException {
        /**
         * Usually, {@link #waitAndDrain()} will only get called once. It blocks for the response for the first time,
         * once the response (the task) reached and being executed waitAndDrain will return, the whole request process
         * then finishes. Subsequent calls on {@link #waitAndDrain()} (if there're any) should return immediately.
         *
         * There's no need to worry that {@link #finished} is not thread-safe. Checking and updating of
         * 'finished' only appear in waitAndDrain, since waitAndDrain is binding to one RPC call (one thread), the call
         * of it is totally sequential.
         */
        if (finished) {// 检测当前ThreadlessExecutor状态
            return;
        }
        // Dubbo 会保证当接口不管是否超时，都会有一个 Runable 的任务被扔到队列里面。所以 take 这里最多也就是等待超时时间这么长时间。--????
        // 如果队列里面没有任务，那么用户线程就会一直在 take 这里阻塞等待 -- 放任务：org.apache.dubbo.common.threadpool.ThreadlessExecutor.execute
        Runnable runnable = queue.take();// 获取阻塞队列中获取任务 -- org.apache.dubbo.remoting.transport.dispatcher.all.AllChannelHandler.received 中提交的任务

        synchronized (lock) {
            waiting = false;// 修改waiting状态
            runnable.run();// 执行任务 -- 注意 run 方法被重写了
        }

        runnable = queue.poll();// 如果阻塞队列中还有其他任务，也需要一并执行
        while (runnable != null) {
            try {
                runnable.run();
            } catch (Throwable t) {
                logger.info(t);

            }
            runnable = queue.poll();
        }
        // mark the status of ThreadlessExecutor as finished.
        finished = true;// 修改finished状态
    }

    public long waitAndDrain(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        /*long startInMs = System.currentTimeMillis();
        Runnable runnable = queue.poll(timeout, unit);
        if (runnable == null) {
            throw new TimeoutException();
        }
        runnable.run();
        long elapsedInMs = System.currentTimeMillis() - startInMs;
        long timeLeft = timeout - elapsedInMs;
        if (timeLeft < 0) {
            throw new TimeoutException();
        }
        return timeLeft;*/
        throw new UnsupportedOperationException();
    }

    /**
     * If the calling thread is still waiting for a callback task, add the task into the blocking queue to wait for schedule.
     * Otherwise, submit to shared callback executor directly.
     *
     * @param runnable
     *
     * 可以调用 ThreadlessExecutor 的execute() 方法，将任务提交给这个线程池，但是这些提交的任务不会被调度到任何线程执行，
     * 而是存储在阻塞队列中，只有当其他线程调用 ThreadlessExecutor.waitAndDrain() 方法时才会真正执行。
     * 也说就是，执行任务的与调用 waitAndDrain() 方法的是同一个线程。
     */
    @Override
    public void execute(Runnable runnable) { //根据 waiting 状态决定任务提交到哪里
        synchronized (lock) {
            if (!waiting) {// 判断业务线程是否还在等待响应结果
                sharedExecutor.execute(runnable);// 不等待，则直接交给共享线程池处理任务
            } else {// 业务线程还在等待，则将任务写入队列，然后由业务线程自己执行
                queue.add(runnable);
            }
        }
    }

    /**
     * tells the thread blocking on {@link #waitAndDrain()} to return, despite of the current status, to avoid endless waiting.
     */
    public void notifyReturn(Throwable t) {
        // an empty runnable task.
        execute(() -> {
            waitingFuture.completeExceptionally(t);
        });
    }

    /**
     * The following methods are still not supported
     */

    @Override
    public void shutdown() {
        shutdownNow();
    }

    @Override
    public List<Runnable> shutdownNow() {
        notifyReturn(new IllegalStateException("Consumer is shutting down and this call is going to be stopped without " +
                "receiving any result, usually this is called by a slow provider instance or bad service implementation."));
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }
}
