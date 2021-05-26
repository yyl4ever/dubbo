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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This class select one provider from multiple providers randomly.
 * You can define weights for each provider:
 * If the weights are all the same then it will use random.nextInt(number of invokers).
 * If the weights are different then it will use random.nextInt(w1 + w2 + ... + wn)
 * Note that if the performance of the machine is better than others, you can set a larger weight.
 * If the performance is not so good, you can set a smaller weight.
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "random";

    /**
     * Select one invoker between a list using a random criteria
     * @param invokers List of possible invokers
     * @param url URL
     * @param invocation Invocation
     * @param <T>
     * @return The selected invoker
     * 计算每个 Invoker 对应的权重值以及总权重值；
     *
     * 当各个 Invoker 权重值不相等时，计算随机数应该落在哪个 Invoker 区间中，返回对应的 Invoker 对象；
     *
     * 当各个 Invoker 权重值相同时，随机返回一个 Invoker 即可。
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // yyl 感觉这里的 sameWeight 是一种优化的措施
        // Every invoker has the same weight?
        boolean sameWeight = true;
        // 计算每个Invoker对象对应的权重，并填充到weights[]数组中
        // the weight of every invokers
        int[] weights = new int[length];
        // 计算第一个Invoker的权重
        // the first invoker's weight
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // totalWeight用于记录总权重值
        // The sum of weights
        int totalWeight = firstWeight;
        for (int i = 1; i < length; i++) {
            // 计算每个Invoker的权重，以及总权重totalWeight
            int weight = getWeight(invokers.get(i), invocation);
            // save for later use
            weights[i] = weight;
            // Sum
            totalWeight += weight;
            // 检测每个Provider的权重是否相同
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        // 各个Invoker权重值不相等时，计算随机数落在哪个区间上
        if (totalWeight > 0 && !sameWeight) {
            // 随机获取一个[0, totalWeight) 区间内的数字
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            // 循环让offset数减去Invoker的权重值，当offset小于0时，返回相应的Invoker
            for (int i = 0; i < length; i++) {
                offset -= weights[i];
                // yyl 窃以为这里要将 invokers 排序??
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // 各个Invoker权重值相同时，随机返回一个Invoker即可
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }

}
