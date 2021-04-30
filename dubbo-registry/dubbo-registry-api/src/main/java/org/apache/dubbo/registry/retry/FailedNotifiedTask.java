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
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.support.FailbackRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FailedNotifiedTask
 */
public final class FailedNotifiedTask extends AbstractRetryTask {

    private static final String NAME = "retry notify";

    private final NotifyListener listener;

    /**
     * 记录当前任务一次运行需要通知的 URL，每执行完一次任务，就会清空该集合
     */
    private final List<URL> urls = new CopyOnWriteArrayList<>();

    public FailedNotifiedTask(URL url, NotifyListener listener) {
        super(url, null, NAME);
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        this.listener = listener;
    }

    public void addUrlToRetry(List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }
        this.urls.addAll(urls);
    }

    public void removeRetryUrl(List<URL> urls) {
        this.urls.removeAll(urls);
    }

    @Override
    protected void doRetry(URL url, FailbackRegistry registry, Timeout timeout) {
        if (CollectionUtils.isNotEmpty(urls)) {
            listener.notify(urls);
            urls.clear();
        }
        reput(timeout, retryPeriod);// 将任务重新添加到时间轮中等待执行
        /**
         * 从上面的代码可以看出，FailedNotifiedTask 重试任务一旦被添加，就会一直运行下去，
         * 但真的是这样吗？在 FailbackRegistry 的 subscribe()、unsubscribe() 方法中，
         * 可以看到 removeFailedNotified() 方法的调用，这里就是清理 FailedNotifiedTask 任务的地方。
         */
    }
}
