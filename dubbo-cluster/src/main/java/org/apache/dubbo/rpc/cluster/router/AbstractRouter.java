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
package org.apache.dubbo.rpc.cluster.router;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;

public abstract class AbstractRouter implements Router {
    /**
     * 路由规则的优先级，用于排序，该字段值越大，优先级越高，默认值为 0
     */
    protected int priority = DEFAULT_PRIORITY;
    /**
     * 当路由结果为空时，是否强制执行。如果不强制执行，则路由结果为空的路由规则将会自动失效；如果强制执行，则直接返回空的路由结果。
     */
    protected boolean force = false;
    /**
     * 路由规则的 URL，可以从 rule 参数中获取具体的路由规则
     */
    protected URL url;

    protected GovernanceRuleRepository ruleRepository;

    public AbstractRouter(URL url) {
        this.ruleRepository = ExtensionLoader.getExtensionLoader(GovernanceRuleRepository.class).getDefaultExtension();
        this.url = url;
    }

    public AbstractRouter() {
    }

    @Override
    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

}
