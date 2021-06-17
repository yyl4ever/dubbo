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
package org.apache.dubbo.rpc.cluster.support;

import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_RETRIES;
import static org.apache.dubbo.common.constants.CommonConstants.RETRIES_KEY;

/**
 * When invoke fails, log the initial error and retry other invokers (retry n times, which means at most n different invokers will be invoked)
 * Note that retry causes latency.
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Failover">Failover</a>
 * 入口
 * FailoverClusterInvoker 会在调用失败的时候，自动切换 Invoker 进行重试。
 */
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private static final Logger logger = LoggerFactory.getLogger(FailoverClusterInvoker.class);

    public FailoverClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyInvokers = invokers;
        // 检查 copyInvokers 是否为空，如果为空会抛出异常
        checkInvokers(copyInvokers, invocation);
        String methodName = RpcUtils.getMethodName(invocation);
        // 最大调用次数 len = 重试次数 + 1，默认重试2次，总共执行3次
        int len = getUrl().getMethodParameter(methodName, RETRIES_KEY, DEFAULT_RETRIES) + 1;
        // 防御性编程
        if (len <= 0) {
            len = 1;
        }
        // 记录最后一次异常
        // retry loop.
        RpcException le = null; // last exception.
        // 记录已经调用过的 invoker 的集合
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyInvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        // 循环调用 len 次，失败重试
        for (int i = 0; i < len; i++) {
            // 第一次传进来的invokers已经check过了，第二次则是重试，需要重新获取最新的服务列表
            //Reselect before retry to avoid a change of candidate `invokers`.
            //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
            // 当调用次数大于 0，才会进入 if 方法
            // 所以能进入 if 方法的时候，一定是失败重试的时候
            if (i > 0) {
                checkWhetherDestroyed();
                // 这里会重新调用Directory.list()方法，获取Invoker列表
                // 在进行重试前重新获取最新的 invoker 集合，这样做的好处是，如果在重试的过程中某个服务挂了，
                // 通过调用 list 方法可以保证 copyInvokers 是最新的可用的 invoker 列表
                copyInvokers = list(invocation);
                // 检查copyInvokers集合是否为空，如果为空会抛出异常
                // check again
                checkInvokers(copyInvokers, invocation);
            }
            // 通过LoadBalance选择Invoker对象，这里传入的invoked集合，
            // 就是前面介绍AbstractClusterInvoker.select()方法中的selected集合
            // 通过负载均衡策略选择一个 invoker
            Invoker<T> invoker = select(loadbalance, invocation, copyInvokers, invoked);
            // 维护已经调用过的 invoker 集合
            // 记录此次要尝试调用的Invoker对象，下一次重试时就会过滤这个服务
            invoked.add(invoker);
            // 把 invoked 放到 RPC 上下文中
            RpcContext.getContext().setInvokers((List) invoked);
            try {
                // 调用选择出来的 invoker 的 invoke 方法，完成远程调用，获取结果
                Result result = invoker.invoke(invocation);
                // 经过尝试之后，终于成功，这里会打印一个警告日志，将尝试过来的Provider地址打印出来
                // 注意：这里打印的是上一次异常的情况，因为只有 invoker.invoke 方法没有抛出异常才会走到这里
                if (le != null && logger.isWarnEnabled()) {
                    logger.warn("Although retry the method " + methodName
                            + " in the service " + getInterface().getName()
                            + " was successful by the provider " + invoker.getUrl().getAddress()
                            + ", but there have been failed providers " + providers
                            + " (" + providers.size() + "/" + copyInvokers.size()
                            + ") from the registry " + directory.getUrl().getAddress()
                            + " on the consumer " + NetUtils.getLocalHost()
                            + " using the dubbo version " + Version.getVersion() + ". Last error is: "
                            + le.getMessage(), le);
                }
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // biz exception.
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                // 抛出异常，表示此次尝试失败，会进行重试 -- 怎么重试的？因为执行成功会立即返回，不成功才会继续循环
                le = new RpcException(e.getMessage(), e);
            } finally {
                // 记录尝试过的Provider地址，会在上面的警告日志中打印出来
                providers.add(invoker.getUrl().getAddress());
            }
        }
        // 达到重试次数上限之后，会抛出异常，其中会携带调用的方法名、尝试过的Provider节点的地址(providers集合)、全部的Provider个数(copyInvokers集合)以及Directory信息
        // 如果经过 for 循环，还是重试失败，那么抛出异常
        throw new RpcException(le.getCode(), "Failed to invoke the method "
                + methodName + " in the service " + getInterface().getName()
                + ". Tried " + len + " times of the providers " + providers
                + " (" + providers.size() + "/" + copyInvokers.size()
                + ") from the registry " + directory.getUrl().getAddress()
                + " on the consumer " + NetUtils.getLocalHost() + " using the dubbo version "
                + Version.getVersion() + ". Last error is: "
                + le.getMessage(), le.getCause() != null ? le.getCause() : le);
    }

}
