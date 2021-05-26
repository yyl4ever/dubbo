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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 *
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";

    private static final int RECYCLE_PERIOD = 60000;

    /**
     * 每个 Provider 节点有两个权重：一个权重是配置的 weight，该值在负载均衡的过程中不会变化；另一个权重是 currentWeight，该值会在负载均衡的过程中动态调整，初始值为 0。
     */
    protected static class WeightedRoundRobin {
        /**
         * 记录配置的权重（weight 字段）
         */
        private int weight;
        /**
         * 随每次负载均衡算法执行变化的 current 权重（current 字段）
         */
        private AtomicLong current = new AtomicLong(0);
        private long lastUpdate;

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }

        public long increaseCurrent() {
            return current.addAndGet(weight);
        }

        public void sel(int total) {
            current.addAndGet(-1 * total);
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    /**
     * yyl : what for?两个 key 各代表什么？第一个 key 代表接口（精确到方法），第二个 key 代表 provider 的 url(对应的 value 就是这个 provider 的权重相关信息)
     */
    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();

    /**
     *
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     *
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            // 服务方法对应的所有的 provider 地址
            return map.keySet();
        }
        return null;
    }

    /**
     * 当前权重列表 = 当前权重 + 配置权重 --》 select max 权重 的 invoker --> 当前权重列表 = 该 invoker 的权重减去 total 权重
     * 思考：1) 代码中没有出现列表，它是怎么存这个权重列表的？ -- 用了 ConcurrentMap 去存储了对应 invoker 的当前权重、配置权重(用了一个对象 WeightedRoundRobin)
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        // 获取整个Invoker列表对应的WeightedRoundRobin映射表，如果为空，则创建一个新的WeightedRoundRobin映射表
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        // 获取当前时间
        long now = System.currentTimeMillis();
        Invoker<T> selectedInvoker = null;
        WeightedRoundRobin selectedWRR = null;
        for (Invoker<T> invoker : invokers) {
            String identifyString = invoker.getUrl().toIdentityString();
            int weight = getWeight(invoker, invocation);
            // 检测当前Invoker是否有相应的WeightedRoundRobin对象，没有则进行创建
            WeightedRoundRobin weightedRoundRobin = map.computeIfAbsent(identifyString, k -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });
            // 检测Invoker权重是否发生了变化，若发生变化，则更新WeightedRoundRobin的weight字段
            if (weight != weightedRoundRobin.getWeight()) {
                //weight changed
                weightedRoundRobin.setWeight(weight);
            }
            // 让currentWeight加上配置的Weight
            long cur = weightedRoundRobin.increaseCurrent();
            //  设置lastUpdate字段
            weightedRoundRobin.setLastUpdate(now);
            // 寻找具有最大currentWeight的Invoker，以及Invoker对应的WeightedRoundRobin
            if (cur > maxCurrent) {
                maxCurrent = cur;
                // 多个 invoker 里面选择一个 cur 最大的
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            // 计算权重总和
            totalWeight += weight;
        }
        // TODO what for?
        if (invokers.size() != map.size()) {
            map.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
        }
        if (selectedInvoker != null) {
            // 用currentWeight减去totalWeight -- 这里因为修改的是引用，所以 map 里面的值也会相应的发生变化，就是选中的 invoker 的当前权重减去总权重
            selectedWRR.sel(totalWeight);
            // 返回选中的Invoker对象
            return selectedInvoker;
        }
        // should not happen here
        return invokers.get(0);
    }

}
