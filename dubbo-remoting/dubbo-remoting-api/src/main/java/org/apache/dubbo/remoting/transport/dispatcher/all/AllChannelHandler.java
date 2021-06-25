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
package org.apache.dubbo.remoting.transport.dispatcher.all;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.ExecutionException;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelEventRunnable;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelEventRunnable.ChannelState;
import org.apache.dubbo.remoting.transport.dispatcher.WrappedChannelHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
// 将所有网络事件以及消息交给关联的线程池进行处理
//AllChannelHandler覆盖了 WrappedChannelHandler 中除了 sent()
// 方法之外的其他网络事件处理方法，将调用其底层的 ChannelHandler 的逻辑放到关联的线程池中执行。

/**
 * AllChannelHandler 并没有覆盖父类的 sent() 方法，也就是说，发送消息是直接在当前线程调用 sent() 方法完成的
 */
public class AllChannelHandler extends WrappedChannelHandler {

    public AllChannelHandler(ChannelHandler handler, URL url) {
        super(handler, url);
    }

    /**
     * 将CONNECTED 事件的处理封装成ChannelEventRunnable提交到线程池中执行
     * @param channel
     * @throws RemotingException
     */
    @Override
    public void connected(Channel channel) throws RemotingException {
        ExecutorService executor = getExecutorService();// 获取公共线程池
        try { // 将CONNECTED事件的处理封装成ChannelEventRunnable提交到线程池中执行
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException("connect event", channel, getClass() + " error when process connected event .", t);
        }
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        ExecutorService executor = getExecutorService();
        try {
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.DISCONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException("disconnect event", channel, getClass() + " error when process disconnected event .", t);
        }
    }

    /**
     * received() 方法会在当前端点收到数据的时候被调用，具体执行流程是先由 IO 线程
     * （也就是 Netty 中的 EventLoopGroup）从二进制流中解码出请求，
     * 然后调用 AllChannelHandler 的 received() 方法，
     * 其中会将请求提交给线程池执行，执行完后调用 sent()方法，向对端写回响应结果。
     *
     * @param channel channel.
     * @param message message.
     * @throws RemotingException
     */
    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        ExecutorService executor = getPreferredExecutorService(message);// 获取线程池
        try {
            // 将消息封装成ChannelEventRunnable任务，提交到线程池中执行 -- Dubbo 默认的派发策略是 ALL，所以所有的响应都会被派发到客户端线程池里面去
            // 当接收到服务端的响应后，响应事件也会被扔到线程池里面，从代码中可以看到，扔进去的就是一个 Runable 任务。 -- 即 ThreadLessExecutor.execute, queue 被添加了任务，消费方阻塞的线程会活过来
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));
        } catch (Throwable t) {
            // 如果线程池满了，请求会被拒绝，这里会根据请求配置决定是否返回一个说明性的响应
        	if(message instanceof Request && t instanceof RejectedExecutionException){
                sendFeedback(channel, (Request) message, t);
                return;
        	}
            throw new ExecutionException(message, channel, getClass() + " error when process received event .", t);
        }
    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        ExecutorService executor = getExecutorService();
        try {
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CAUGHT, exception));
        } catch (Throwable t) {
            throw new ExecutionException("caught event", channel, getClass() + " error when process caught event .", t);
        }
    }
}
