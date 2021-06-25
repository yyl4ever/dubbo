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
package org.apache.dubbo.remoting.exchange.support;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.ThreadlessExecutor;
import org.apache.dubbo.common.timer.HashedWheelTimer;
import org.apache.dubbo.common.timer.Timeout;
import org.apache.dubbo.common.timer.Timer;
import org.apache.dubbo.common.timer.TimerTask;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.TimeoutException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * DefaultFuture.
 */
public class DefaultFuture extends CompletableFuture<Object> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFuture.class);
    /**
     * 管理请求与 Channel 之间的关联关系，其中 Key 为请求 ID，Value 为发送请求的 Channel。
     */
    private static final Map<Long, Channel> CHANNELS = new ConcurrentHashMap<>();
    /**
     * 管理请求与 DefaultFuture 之间的关联关系，其中 Key 为请求 ID，Value 为请求对应的 Future。
     */
    private static final Map<Long, DefaultFuture> FUTURES = new ConcurrentHashMap<>();
    /**
     * static 字段，Dubbo 中对时间轮的实现, 所有 DefaultFuture 对象共用一个 -- 你发起一个请求，指定时间内没有返回结果，于是就取消（future.cancel）这个请求。
     */
    public static final Timer TIME_OUT_TIMER = new HashedWheelTimer(
            new NamedThreadFactory("dubbo-future-timeout", true),
            30,
            TimeUnit.MILLISECONDS);

    // invoke id. 请求以及请求的 ID
    private final Long id;
    // 发送请求的 Channel
    private final Channel channel;
    // 请求以及请求的 ID
    private final Request request;
    // 整个请求-响应交互完成的超时时间
    private final int timeout;
    // 该 DefaultFuture 的创建时间
    private final long start = System.currentTimeMillis();
    // 请求发送的时间
    private volatile long sent;
    // 该定时任务到期时，表示对端响应超时。
    private Timeout timeoutCheckTask;
    // 请求关联的线程池
    private ExecutorService executor;

    public ExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    private DefaultFuture(Channel channel, Request request, int timeout) {
        this.channel = channel;
        this.request = request;
        this.id = request.getId();
        this.timeout = timeout > 0 ? timeout : channel.getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        // put into waiting map.
        FUTURES.put(id, this);// this 还在初始化的过程中
        CHANNELS.put(id, channel);
    }

    /**
     * check time out of the future
     */
    private static void timeoutCheck(DefaultFuture future) {
        //创建 TimeoutCheckTask 定时任务，并添加到时间轮中
        TimeoutCheckTask task = new TimeoutCheckTask(future.getId());
        //TIME_OUT_TIMER 是一个 HashedWheelTimer 对象
        future.timeoutCheckTask = TIME_OUT_TIMER.newTimeout(task, future.getTimeout(), TimeUnit.MILLISECONDS);
    }

    /**
     * init a DefaultFuture
     * 1.init a DefaultFuture 初始化 DefaultFuture 。
     * 2.timeout check 检测是否超时。
     *
     * 先初始化上述字段，并创建请求相应的超时定时任务
     *
     * @param channel channel
     * @param request the request
     * @param timeout timeout
     * @return a new DefaultFuture
     */
    public static DefaultFuture newFuture(Channel channel, Request request, int timeout, ExecutorService executor) {
        //// 创建DefaultFuture对象，并初始化其中的核心字段 -- 初始化 DefaultFuture，不简单
        final DefaultFuture future = new DefaultFuture(channel, request, timeout);
        future.setExecutor(executor);
        //    // 对于ThreadlessExecutor的特殊处理，ThreadlessExecutor可以关联一个waitingFuture，就是这里创建DefaultFuture对象
        // ThreadlessExecutor needs to hold the waiting future in case of circuit return.
        if (executor instanceof ThreadlessExecutor) {
            ((ThreadlessExecutor) executor).setWaitingFuture(future);
        }
        // timeout check
        // 创建一个定时任务，用处理响应超时的情况
        timeoutCheck(future);
        return future;
    }

    public static DefaultFuture getFuture(long id) {
        return FUTURES.get(id);
    }

    public static boolean hasFuture(Channel channel) {
        return CHANNELS.containsValue(channel);
    }

    public static void sent(Channel channel, Request request) {
        DefaultFuture future = FUTURES.get(request.getId());
        if (future != null) {
            future.doSent();
        }
    }

    /**
     * close a channel when a channel is inactive
     * directly return the unfinished requests.
     *
     * @param channel channel to close
     */
    public static void closeChannel(Channel channel) {
        for (Map.Entry<Long, Channel> entry : CHANNELS.entrySet()) {
            if (channel.equals(entry.getValue())) {
                DefaultFuture future = getFuture(entry.getKey());
                if (future != null && !future.isDone()) {
                    ExecutorService futureExecutor = future.getExecutor();
                    if (futureExecutor != null && !futureExecutor.isTerminated()) {
                        futureExecutor.shutdownNow();
                    }

                    Response disconnectResponse = new Response(future.getId());
                    disconnectResponse.setStatus(Response.CHANNEL_INACTIVE);
                    disconnectResponse.setErrorMessage("Channel " +
                            channel +
                            " is inactive. Directly return the unFinished request : " +
                            future.getRequest());
                    DefaultFuture.received(channel, disconnectResponse);
                }
            }
        }
    }

    public static void received(Channel channel, Response response) {
        received(channel, response, false);
    }

    /**
     * 全局只有两个调用的地方，一个是前面讲的正常返回后的调用，一个就是这里超时之后的调用
     * 也就是不论怎样，最终都会调用这个 received 方法，最终都会通过这个方法把对应调用编号的 DefaultFuture 对象从 FUTURE 这个 MAP 中移除。
     * @param channel
     * @param response
     * @param timeout
     */
    public static void received(Channel channel, Response response, boolean timeout) {
        try {
            // 清理FUTURES中记录的请求ID与DefaultFuture之间的映射关系
            DefaultFuture future = FUTURES.remove(response.getId()); // 调用编号从 FUTURES 这个 MAP 中移除并获取出对应的请求。
            if (future != null) {
                Timeout t = future.timeoutCheckTask;
                if (!timeout) {// 未超时，取消定时任务
                    // decrease Time
                    t.cancel();
                }
                // 把响应返回给对应的用户线程了
                future.doReceived(response);
            } else {// 查找不到关联的DefaultFuture会打印日志 -- 如果获取到的请求是 null，说明超时了。
                logger.warn("The timeout response finally returned at "
                        + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                        + ", response status is " + response.getStatus()
                        + (channel == null ? "" : ", channel: " + channel.getLocalAddress()
                        + " -> " + channel.getRemoteAddress()) + ", please check provider side for detailed result.");
            }
        } finally {
            CHANNELS.remove(response.getId());// 清理CHANNELS中记录的请求ID与Channel之间的映射关系
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Response errorResult = new Response(id);
        errorResult.setStatus(Response.CLIENT_ERROR);
        errorResult.setErrorMessage("request future has been canceled.");
        this.doReceived(errorResult);
        FUTURES.remove(id);
        CHANNELS.remove(id);
        return true;
    }

    public void cancel() {
        this.cancel(true);
    }

    private void doReceived(Response res) {
        if (res == null) {
            throw new IllegalStateException("response cannot be null");
        }
        if (res.getStatus() == Response.OK) { // 正常响应
            this.complete(res.getResult());
        } else if (res.getStatus() == Response.CLIENT_TIMEOUT || res.getStatus() == Response.SERVER_TIMEOUT) {
            // 超时
            this.completeExceptionally(new TimeoutException(res.getStatus() == Response.SERVER_TIMEOUT, channel, res.getErrorMessage()));
        } else {// 其他异常
            this.completeExceptionally(new RemotingException(channel, res.getErrorMessage()));
        }
        // 下面是针对ThreadlessExecutor的兜底处理，主要是防止业务线程一直阻塞在ThreadlessExecutor上
        // the result is returning, but the caller thread may still waiting
        // to avoid endless waiting for whatever reason, notify caller thread to return.
        if (executor != null && executor instanceof ThreadlessExecutor) {
            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor) executor;
            if (threadlessExecutor.isWaiting()) {
                // notifyReturn()方法会向ThreadlessExecutor提交一个任务，这样业务线程就不会阻塞了，提交的任务会尝试将DefaultFuture设置为异常结束
                threadlessExecutor.notifyReturn(new IllegalStateException("The result has returned, but the biz thread is still waiting" +
                        " which is not an expected state, interrupt the thread manually by returning an exception."));
            }
        }
    }

    private long getId() {
        return id;
    }

    private Channel getChannel() {
        return channel;
    }

    private boolean isSent() {
        return sent > 0;
    }

    public Request getRequest() {
        return request;
    }

    private int getTimeout() {
        return timeout;
    }

    private void doSent() {
        sent = System.currentTimeMillis();
    }

    private String getTimeoutMessage(boolean scan) {
        long nowTimestamp = System.currentTimeMillis();
        return (sent > 0 ? "Waiting server-side response timeout" : "Sending request timeout in client-side")
                + (scan ? " by scan timer" : "") + ". start time: "
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(start))) + ", end time: "
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(nowTimestamp))) + ","
                + (sent > 0 ? " client elapsed: " + (sent - start)
                + " ms, server elapsed: " + (nowTimestamp - sent)
                : " elapsed: " + (nowTimestamp - start)) + " ms, timeout: "
                + timeout + " ms, request: " + (logger.isDebugEnabled() ? request : getRequestWithoutData()) + ", channel: " + channel.getLocalAddress()
                + " -> " + channel.getRemoteAddress();
    }

    private Request getRequestWithoutData() {
        Request newRequest = request;
        newRequest.setData(null);
        return newRequest;
    }
    //实现了 TimerTask 接口，可以提交到时间轮中等待执行。
    private static class TimeoutCheckTask implements TimerTask {

        private final Long requestID;

        TimeoutCheckTask(Long requestID) {
            this.requestID = requestID;
        }

        /**
         * 触发后会根据调用编号去 FUTURES 里面取 DefaultFuture。
         * @param timeout a handle which is associated with this task
         */
        @Override
        public void run(Timeout timeout) {
            DefaultFuture future = DefaultFuture.getFuture(requestID);
            //// 检查该任务关联的DefaultFuture对象是否已经完成
            if (future == null || future.isDone()) {
                return;
            }

            if (future.getExecutor() != null) {// 提交到线程池执行，注意ThreadlessExecutor的情况
                future.getExecutor().execute(() -> notifyTimeout(future));
            } else {
                // 当响应超时的时候，TimeoutCheckTask 会创建一个 Response，并调用前面介绍的 DefaultFuture.received() 方法。
                /**
                 * 如果一个 future 正常完成之后，会从 FUTURES 里面移除掉。
                 *
                 * 那么如果到点了，根据编号没有取到 Future 或者取到的这个 Future 的状态是 done 了，则说明这个请求没有超时。
                 *
                 * 如果这个 Future 还在 FUTURES 里面，含义就是到点了你咋还在里面呢？那肯定是超时了，调用 notifyTimeout 方法，是否超时参数给 true
                 */
                notifyTimeout(future);
            }
        }

        private void notifyTimeout(DefaultFuture future) {
            // 没有收到对端的响应，这里会创建一个Response，表示超时的响应
            // create exception response.
            Response timeoutResponse = new Response(future.getId());
            // set timeout status.
            timeoutResponse.setStatus(future.isSent() ? Response.SERVER_TIMEOUT : Response.CLIENT_TIMEOUT);
            timeoutResponse.setErrorMessage(future.getTimeoutMessage(true));
            // 将关联的DefaultFuture标记为超时异常完成
            // handle response.
            DefaultFuture.received(future.getChannel(), timeoutResponse, true);
        }
    }
}
