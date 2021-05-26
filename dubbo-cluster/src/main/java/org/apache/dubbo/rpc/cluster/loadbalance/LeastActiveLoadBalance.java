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
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 当前活跃请求数越小的 Provider 节点，剩余的处理能力越多，处理请求的效率也就越高，那么该 Provider 在单位时间内就可以处理更多的请求，所以我们应该优先将请求分配给该 Provider 节点。
 * LeastActiveLoadBalance
 * <p>
 * Filter the number of invokers with the least number of active calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";

    /**
     * 首先会选出所有活跃请求数最小的 Invoker 对象，之后的逻辑与 RandomLoadBalance 完全一样，即按照这些 Invoker 对象的权重挑选最终的 Invoker 对象。
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // 初始化Invoker数量
        // Number of invokers
        int length = invokers.size();
        // 记录最小的活跃请求数
        // 最小活跃数
        // The least active value of all invokers
        int leastActive = -1;
        // 记录活跃请求数最小的Invoker集合的个数 -- 因为可以多个
        // The number of invokers having the same least active value (leastActive)
        int leastCount = 0;
        // 记录活跃请求数最小的Invoker在invokers数组中的下标位置
        // The index of invokers having the same least active value (leastActive)
        int[] leastIndexes = new int[length];
        // 记录活跃请求数最小的Invoker集合中，每个Invoker的权重值
        // the weight of every invokers
        int[] weights = new int[length];
        // 记录活跃请求数最小的Invoker集合中，所有Invoker的权重值之和
        // The sum of the warmup weights of all the least active invokers
        int totalWeight = 0;
        // 记录活跃请求数最小的Invoker集合中，第一个Invoker的权重值
        // The weight of the first least active invoker
        int firstWeight = 0;
        // 活跃请求数最小的集合中，所有Invoker的权重值是否相同
        // Every least active invoker has the same weight value?
        boolean sameWeight = true;


        // Filter out all the least active invokers
        // 遍历所有Invoker，获取活跃请求数最小的Invoker集合
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // 获取该Invoker的活跃请求数 -- TODO yyl 咋算出来的？为什么用的是客户端限流组件 ActiveLimitFilter?不应该看服务端的调度能力吗？
            // Get the active number of the invoker
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // 获取该Invoker的权重
            // Get the weight of the invoker's configuration. The default value is 100.
            int afterWarmup = getWeight(invoker, invocation);
            // save for later use
            weights[i] = afterWarmup;
            // 比较活跃请求数
            // If it is the first invoker or the active number of the invoker is less than the current least active number
            if (leastActive == -1 || active < leastActive) {
                // 当前的Invoker是第一个活跃请求数最小的Invoker，则记录如下信息
                // 重新记录最小的活跃请求数
                // Reset the active number of the current invoker to the least active number
                leastActive = active;
                // 重新记录活跃请求数最小的Invoker集合个数
                // Reset the number of least active invokers
                leastCount = 1;
                // 重新记录Invoker
                // Put the first least active invoker first in leastIndexes
                leastIndexes[0] = i;
                // 重新记录总权重值
                // Reset totalWeight
                totalWeight = afterWarmup;
                // 该Invoker作为第一个Invoker，记录其权重值
                // Record the weight the first least active invoker
                firstWeight = afterWarmup;
                // 重新记录是否权重值相等
                // Each invoke has the same weight (only one invoker here)
                sameWeight = true;
                // If current invoker's active value equals with leaseActive, then accumulating.
            } else if (active == leastActive) {
                // 当前Invoker属于活跃请求数最小的Invoker集合
                // 记录该Invoker的下标
                // Record the index of the least active invoker in leastIndexes order
                leastIndexes[leastCount++] = i;
                // 更新总权重
                // Accumulate the total weight of the least active invoker
                totalWeight += afterWarmup;
                // 活跃调用数相同，但当前 Invoker 的权重不等于上次算出的拥有最小活跃调用数 的 Invoker
                // If every invoker has the same weight?
                if (sameWeight && afterWarmup != firstWeight) {
                    // 更新权重值是否相等
                    sameWeight = false;
                }
            }
        }
        // 如果只有一个活跃请求数最小的Invoker对象，直接返回即可
        // Choose an invoker from all the least active invokers
        if (leastCount == 1) {
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexes[0]);
        }
        // 下面按照RandomLoadBalance的逻辑，从活跃请求数最小的Invoker集合中，随机选择一个Invoker对象返回
        // 有多个拥有最小活跃请求数的Invoker对象，但是他们的权重不同，就从他们里面利用随机加权算法选择一个
        if (!sameWeight && totalWeight > 0) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on 
            // totalWeight.
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
