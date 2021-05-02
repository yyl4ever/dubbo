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
package org.apache.dubbo.common.concurrent;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.junit.jupiter.api.Test;


public class CompletableFutureTaskTest {

    private static final ExecutorService executor = new ThreadPoolExecutor(0, 10, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new NamedThreadFactory("DubboMonitorCreator", true));

    @Test
    public void testCreate() throws InterruptedException {

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        // 因为 CompletableFuture 实现了 CompletionStage 和 Future 接口，所以它还是可以像以前一样通过 get() 阻塞或者 isDone() 方法轮询的方式获得结果，这就保证了同步调用依旧可用。当然，在实际工作中，不是很建议用 get() 这样阻塞的方式来获取结果，因为这样就丢失了异步操作带来的性能提升。
        // 另外，CompletableFuture 提供了良好的回调方法，例如，whenComplete()、whenCompleteAsync() 等方法都可以在逻辑完成后，执行该方法中添加的 action 逻辑，实现回调的逻辑。同时，CompletableFuture 很好地支持了 Future 间的相互协调或组合，例如，thenApply()、thenApplyAsync() 等方法。
        CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(() -> {
            countDownLatch.countDown();
            return true;
        }, executor);
        countDownLatch.await();
    }

    @Test
    public void testRunnableResponse() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }, executor);

        Boolean result = completableFuture.get();
        assertThat(result, is(true));
    }

    @Test
    public void testListener() throws InterruptedException {
        CompletableFuture<String> completableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "hello";

        }, executor);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        completableFuture.thenRunAsync(countDownLatch::countDown);
        countDownLatch.await();
    }


@Test
    public void testCustomExecutor() {
        Executor mockedExecutor = mock(Executor.class);
        CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
            return 0;
        });
        completableFuture.thenRunAsync(mock(Runnable.class), mockedExecutor).whenComplete((s, e) ->
                verify(mockedExecutor, times(1)).execute(any(Runnable.class)));
    }
}