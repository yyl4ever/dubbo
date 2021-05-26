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
package org.apache.dubbo.rpc.cluster.router.condition.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;

/**
 * Service level router, "server-unique-name.condition-router"
 * ServiceRouter 和 AppRouter 都是简单地继承了 ListenableRouter 抽象类，且没有覆盖 ListenableRouter 的任何方法，两者只有以下两点区别。
 *
 * 一个是 priority 字段值不同。ServiceRouter 为 140，AppRouter 为 150，也就是说 ServiceRouter 要先于 AppRouter 执行。
 *
 * 另一个是获取 ConditionRouterRule 配置的 Key 不同。
 * ServiceRouter 使用的 RuleKey 是由 {interface}:[version]:[group] 三部分构成，
 * 获取的是一个服务对应的 ConditionRouterRule。
 * AppRouter 使用的 RuleKey 是 URL 中的 application 参数值，
 * 获取的是一个服务实例对应的 ConditionRouterRule。
 */
public class ServiceRouter extends ListenableRouter {
    public static final String NAME = "SERVICE_ROUTER";
    /**
     * ServiceRouter should before AppRouter
     */
    private static final int SERVICE_ROUTER_DEFAULT_PRIORITY = 140;

    public ServiceRouter(URL url) {
        super(url, DynamicConfiguration.getRuleKey(url));
        this.priority = SERVICE_ROUTER_DEFAULT_PRIORITY;
    }
}
