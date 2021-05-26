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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_LOADBALANCE;
import static org.apache.dubbo.common.constants.CommonConstants.LOADBALANCE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CLUSTER_AVAILABLE_CHECK_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CLUSTER_STICKY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_CLUSTER_AVAILABLE_CHECK;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_CLUSTER_STICKY;

/**
 * AbstractClusterInvoker
 * 两点核心功能：一个是实现的 Invoker 接口，对 Invoker.invoke() 方法进行通用的抽象实现；另一个是实现通用的负载均衡算法。
 */
public abstract class AbstractClusterInvoker<T> implements Invoker<T> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractClusterInvoker.class);

    protected Directory<T> directory;

    protected boolean availablecheck;

    private AtomicBoolean destroyed = new AtomicBoolean(false);

    private volatile Invoker<T> stickyInvoker = null;

    public AbstractClusterInvoker() {
    }

    public AbstractClusterInvoker(Directory<T> directory) {
        this(directory, directory.getUrl());
    }

    public AbstractClusterInvoker(Directory<T> directory, URL url) {
        if (directory == null) {
            throw new IllegalArgumentException("service directory == null");
        }

        this.directory = directory;
        //sticky: invoker.isAvailable() should always be checked before using when availablecheck is true.
        this.availablecheck = url.getParameter(CLUSTER_AVAILABLE_CHECK_KEY, DEFAULT_CLUSTER_AVAILABLE_CHECK);
    }

    @Override
    public Class<T> getInterface() {
        return directory.getInterface();
    }

    @Override
    public URL getUrl() {
        return directory.getConsumerUrl();
    }

    public URL getRegistryUrl() {
        return directory.getUrl();
    }

    @Override
    public boolean isAvailable() {
        Invoker<T> invoker = stickyInvoker;
        if (invoker != null) {
            return invoker.isAvailable();
        }
        return directory.isAvailable();
    }

    public Directory<T> getDirectory() {
        return directory;
    }

    @Override
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            directory.destroy();
        }
    }

    /**
     * 在 select() 方法中会根据配置决定是否开启粘滞连接特性，如果开启了，
     * 则需要将上次使用的 Invoker 缓存起来，只要 Provider 节点可用就直接调用，不会再进行负载均衡。如果调用失败，才会重新进行负载均衡，并且排除已经重试过的 Provider 节点。
     *
     * Select a invoker using loadbalance policy.</br>
     * a) Firstly, select an invoker using loadbalance. If this invoker is in previously selected list, or,
     * if this invoker is unavailable, then continue step b (reselect), otherwise return the first selected invoker</br>
     * <p>
     * b) Reselection, the validation rule for reselection: selected > available. This rule guarantees that
     * the selected invoker has the minimum chance to be one in the previously selected list, and also
     * guarantees this invoker is available.
     *
     * @param loadbalance load balance policy 负载均衡策略
     * @param invocation  invocation 它持有调用过程中的变量，比如方法名，参数等
     * @param invokers    invoker candidates 可以看成是存活着的服务提供者列表
     * @param selected    exclude selected invokers or not 已经被选择过的 invoker 集合 -- 为了不要再选它？？
     * @return the invoker which will final to do invoke.
     * @throws RpcException exception
     * // 第一个参数是此次使用的LoadBalance实现，第二个参数Invocation是此次服务调用的上下文信息，
     * // 第三个参数是待选择的Invoker集合，第四个参数用来记录负载均衡已经选出来、尝试过的Invoker集合
     */
    protected Invoker<T> select(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {

        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        // 获取调用方法名
        String methodName = invocation == null ? StringUtils.EMPTY_STRING : invocation.getMethodName();

        // 获取sticky配置，sticky表示粘滞连接，所谓粘滞连接是指Consumer会尽可能地
        // 调用同一个Provider节点，除非这个Provider无法提供服务

        // 获取 sticky 配置，sticky 标识粘滞连接。
        // 所谓粘滞连接是指让服务消费者尽可能的调用同一个服务提供者
        // 除非该提供者挂了再进行切换
        boolean sticky = invokers.get(0).getUrl()
                .getMethodParameter(methodName, CLUSTER_STICKY_KEY, DEFAULT_CLUSTER_STICKY);

        // 检测invokers列表是否包含sticky Invoker，如果不包含，
        // 说明stickyInvoker代表的服务提供者挂了，此时需要将其置空

        // 检测 invokers 列表是否包含 stickyInvoker，如果不包含，
        // 说明 stickyInvoker 代表的服务提供者挂了，此时需要将其置空
        //ignore overloaded method
        if (stickyInvoker != null && !invokers.contains(stickyInvoker)) {
            stickyInvoker = null;
        }

        // 如果开启了粘滞连接特性，需要先判断这个Provider节点是否已经重试过了


        // 在 sticky 为 true，且 stickyInvoker != null 的情况下，如果 selected 包含 stickyInvoker，
        // 表明 stickyInvoker 对应的服务提供者之前调用的时候，可能因网络原因未能成功提供服务，
        // 但是该提供者并没有挂，此时 invokers 列表中仍存在该服务提供者对应的 invoker -- 如果 selected 包含 stickyInvoker，
        // 则说明 stickyInvoker
        // 虽然还活着，但是之前没有成功提供服务，此时我们认为这个服务不可靠，不应该在重试期间内再次被调用
        //ignore concurrency problem
        if (sticky && stickyInvoker != null && (selected == null || !selected.contains(stickyInvoker))) {
            // 检测当前stickyInvoker是否可用，如果可用，直接返回stickyInvoker

            // availablecheck 表示是否开启了可用性检查，如果开启了，
            // 则调用 stickyInvoker 的 isAvailable 进行检查，如果检查通过，则直接返回 stickyInvoker
            if (availablecheck && stickyInvoker.isAvailable()) {
                return stickyInvoker;
            }
        }

        // 执行到这里，说明前面的stickyInvoker为空，或者不可用
        // 这里会继续调用doSelect选择新的Invoker对象

        // 如果线程走到当期代码，说明 stickyInvoker 为空，或者不可用。
        // 此时继续调用 doSelect 选择 invoker
        Invoker<T> invoker = doSelect(loadbalance, invocation, invokers, selected);

        // 是否开启粘滞，更新stickyInvoker字段
        // 如果 sticky 为 true，则将负载均衡组件选出的 invoker 赋值给 stickyInvoker
        if (sticky) {
            stickyInvoker = invoker;
        }
        return invoker;
    }

    /**
     * 一是通过 LoadBalance 选择 Invoker 对象；
     * 二是如果选出来的 Invoker 不稳定或不可用，会调用 reselect() 方法进行重选。
     * @param loadbalance
     * @param invocation
     * @param invokers
     * @param selected
     * @return
     * @throws RpcException
     */
    private Invoker<T> doSelect(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected) throws RpcException {
        // 判断是否需要进行负载均衡，Invoker集合为空，直接返回null

        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        // 如果只有一个服务提供者，直接返回
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        // 通过LoadBalance实现选择Invoker对象

        // 如果多于一个，则通过负载均衡策略选择出一个 invoker -- 第一次选择
        Invoker<T> invoker = loadbalance.select(invokers, getUrl(), invocation);

        // 如果LoadBalance选出的Invoker对象，已经尝试过请求了或不可用，则需要调用reselect()方法重选

        // 如果 selected 包含负载均衡选择出的 invoker，或者选择出的 invoker 无法经过可用性检查，此时进行重选
        //If the `invoker` is in the  `selected` or invoker is unavailable && availablecheck is true, reselect.
        if ((selected != null && selected.contains(invoker))
                || (!invoker.isAvailable() && getUrl() != null && availablecheck)) {
            try {
                // 进行重选 -- 第二次选择
                Invoker<T> rInvoker = reselect(loadbalance, invocation, invokers, selected, availablecheck);
                // 如果重选的Invoker对象不为空，则直接返回这个 rInvoker
                if (rInvoker != null) {
                    invoker = rInvoker;
                } else {
                    // 重选出来的 rInvoker 为空，则获取重选之前的 invoker 在集合中的坐标
                    //Check the index of current selected invoker, if it's not the last one, choose the one at index+1.
                    int index = invokers.indexOf(invoker);
                    try {
                        // 如果重选的Invoker对象为空，则返回该Invoker的下一个Invoker对象
                        // 获取 index + 1 位置处的 invoker -- -- 第三次选择，最多会进行三次选择
                        //Avoid collision
                        invoker = invokers.get((index + 1) % invokers.size());
                    } catch (Exception e) {
                        logger.warn(e.getMessage() + " may because invokers list dynamic change, ignore.", e);
                    }
                }
            } catch (Throwable t) {
                logger.error("cluster reselect fail reason is :" + t.getMessage() + " if can not solve, you can set cluster.availablecheck=false in url", t);
            }
        }
        return invoker;
    }

    /**
     * Reselect, use invokers not in `selected` first, if all invokers are in `selected`,
     * just pick an available one using loadbalance policy.
     *
     * @param loadbalance    load balance policy
     * @param invocation     invocation
     * @param invokers       invoker candidates
     * @param selected       exclude selected invokers or not
     * @param availablecheck check invoker available if true
     * @return the reselect result to do invoke
     * @throws RpcException exception
     */
    private Invoker<T> reselect(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers, List<Invoker<T>> selected, boolean availablecheck) throws RpcException {

        // 可以参与重选的 invoker 的集合
        // 其元素为所有可用的 invoker 的集合与 selected 集合的差集
        //Allocating one in advance, this list is certain to be used.
        List<Invoker<T>> reselectInvokers = new ArrayList<>(
                invokers.size() > 1 ? (invokers.size() - 1) : invokers.size());

        // 遍历 invokers 列表
        // First, try picking a invoker not in `selected`.
        for (Invoker<T> invoker : invokers) {
            // 进行可用性检测
            if (availablecheck && !invoker.isAvailable()) {
                continue;
            }
            // 如果 selected 列表不包含当前 invoker 则将其添加到 reselectInvokers 中
            if (selected == null || !selected.contains(invoker)) {
                reselectInvokers.add(invoker);
            }
        }

        // reselectInvokers 不为空，此时通过负载均衡组件进行选择
        if (!reselectInvokers.isEmpty()) {
            return loadbalance.select(reselectInvokers, getUrl(), invocation);
        }

        // 若线程走到此处，说明 reselectInvokers 集合为空，
        // 说明所有的可用的 invoker 都已经被调用过，都在 selected 集合中。
        // 所以这里从  selected 列表中查找可用的 invoker，并将其添加到 reselectInvokers 集合中
        // Just pick an available invoker using loadbalance policy
        if (selected != null) {
            for (Invoker<T> invoker : selected) {
                if ((invoker.isAvailable()) // available first
                        && !reselectInvokers.contains(invoker)) {
                    reselectInvokers.add(invoker);
                }
            }
        }
        if (!reselectInvokers.isEmpty()) {
            // 重选，通过负载均衡组件选择 Invoker
            return loadbalance.select(reselectInvokers, getUrl(), invocation);
        }

        return null;
    }

    /**
     * 通过 Directory 获取 Invoker 列表，然后通过 SPI 初始化 LoadBalance，最后调用 doInvoke() 方法执行子类的逻辑。
     * @param invocation
     * @return
     * @throws RpcException
     */
    @Override
    public Result invoke(final Invocation invocation) throws RpcException {
        // 检测当前Invoker是否已销毁
        checkWhetherDestroyed();
        // 将RpcContext中的attachment添加到Invocation中
        // binding attachments into invocation.
        Map<String, Object> contextAttachments = RpcContext.getContext().getObjectAttachments();
        if (contextAttachments != null && contextAttachments.size() != 0) {
            ((RpcInvocation) invocation).addObjectAttachments(contextAttachments);
        }
        // 通过Directory获取Invoker对象列表，通过对RegistryDirectory的介绍我们知道，其中已经调用了Router进行过滤
        List<Invoker<T>> invokers = list(invocation);
        // 通过SPI加载LoadBalance
        LoadBalance loadbalance = initLoadBalance(invokers, invocation);
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        // 调用doInvoke()方法，该方法是个抽象方法
        return doInvoke(invocation, invokers, loadbalance);
    }

    protected void checkWhetherDestroyed() {
        if (destroyed.get()) {
            throw new RpcException("Rpc cluster invoker for " + getInterface() + " on consumer " + NetUtils.getLocalHost()
                    + " use dubbo version " + Version.getVersion()
                    + " is now destroyed! Can not invoke any more.");
        }
    }

    @Override
    public String toString() {
        return getInterface() + " -> " + getUrl().toString();
    }

    protected void checkInvokers(List<Invoker<T>> invokers, Invocation invocation) {
        if (CollectionUtils.isEmpty(invokers)) {
            throw new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "Failed to invoke the method "
                    + invocation.getMethodName() + " in the service " + getInterface().getName()
                    + ". No provider available for the service " + directory.getConsumerUrl().getServiceKey()
                    + " from registry " + directory.getUrl().getAddress()
                    + " on the consumer " + NetUtils.getLocalHost()
                    + " using the dubbo version " + Version.getVersion()
                    + ". Please check if the providers have been started and registered.");
        }
    }

    /**
     * 按照不同的 LoadBalance 算法从 Invoker 集合中选取最终 Invoker 对象
     * @param invocation
     * @param invokers
     * @param loadbalance
     * @return
     * @throws RpcException
     */
    protected abstract Result doInvoke(Invocation invocation, List<Invoker<T>> invokers,
                                       LoadBalance loadbalance) throws RpcException;

    protected List<Invoker<T>> list(Invocation invocation) throws RpcException {
        // 根据 invocation 找到所有的 Invoker？？-- 在 Directory.list() 方法返回 Invoker 集合之前，已经使用 Router 进行了一次筛选
        return directory.list(invocation);
    }

    /**
     * Init LoadBalance.
     * <p>
     * if invokers is not empty, init from the first invoke's url and invocation
     * if invokes is empty, init a default LoadBalance(RandomLoadBalance)
     * </p>
     *
     * @param invokers   invokers
     * @param invocation invocation
     * @return LoadBalance instance. if not need init, return null.
     */
    protected LoadBalance initLoadBalance(List<Invoker<T>> invokers, Invocation invocation) {
        if (CollectionUtils.isNotEmpty(invokers)) {
            return ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                    .getMethodParameter(RpcUtils.getMethodName(invocation), LOADBALANCE_KEY, DEFAULT_LOADBALANCE));
        } else {
            return ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(DEFAULT_LOADBALANCE);
        }
    }
}
