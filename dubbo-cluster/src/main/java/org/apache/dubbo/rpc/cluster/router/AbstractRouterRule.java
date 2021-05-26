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

/**
 * TODO Extract more code here if necessary
 */
public abstract class AbstractRouterRule {
    /**
     * 记录了路由规则解析前的原始字符串配置
     */
    private String rawRule;
    /**
     * 表示是否在每次调用时执行该路由规则。如果设置为 false，则会在 Provider 列表变更时预先执行并缓存结果，调用时直接从缓存中获取路由结果。
     */
    private boolean runtime = true;
    /**
     * 当路由结果为空时，是否强制执行，如果不强制执行，路由结果为空的路由规则将自动失效。该字段默认值为 false。
     */
    private boolean force = false;
    /**
     * 用于标识解析生成当前 RouterRule 对象的配置是否合法。
     */
    private boolean valid = true;
    /**
     * 标识当前路由规则是否生效。
     */
    private boolean enabled = true;
    /**
     * 用于表示当前 RouterRule 的优先级。
     */
    private int priority;
    /**
     * 表示该路由规则是否为持久数据，当注册方退出时，路由规则是否依然存在。
     */
    private boolean dynamic = false;
    /**
     * scope 为 service 时，key 由 [{group}:]{service}[:{version}] 构成；scope 为 application 时，key 为 application 的名称。
     */
    private String scope;
    private String key;

    public String getRawRule() {
        return rawRule;
    }

    public void setRawRule(String rawRule) {
        this.rawRule = rawRule;
    }

    public boolean isRuntime() {
        return runtime;
    }

    public void setRuntime(boolean runtime) {
        this.runtime = runtime;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
