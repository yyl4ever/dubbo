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

package org.apache.dubbo.registry.retry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.timer.Timeout;
import org.apache.dubbo.registry.support.FailbackRegistry;

/**
 * FailedRegisteredTask
 */
public final class FailedRegisteredTask extends AbstractRetryTask {

    private static final String NAME = "retry register";

    public FailedRegisteredTask(URL url, FailbackRegistry registry) {
        super(url, registry, NAME);
    }

    /**
     * 执行关联 Registry 的 doRegister() 方法，完成与服务发现组件交互。
     *
     * @param url
     * @param registry
     * @param timeout
     */
    @Override
    protected void doRetry(URL url, FailbackRegistry registry, Timeout timeout) {
        registry.doRegister(url);// 重新注册
        /**
         * 如果注册成功，则会调用 removeFailedRegisteredTask()
         * 方法将当前关联的 URL 以及当前重试任务从 failedRegistered 集合中删除。如果注册失败，则会抛出异常，执行上文介绍的 reput ()方法重试。
         */
        registry.removeFailedRegisteredTask(url);// 删除重试任务
    }
}
