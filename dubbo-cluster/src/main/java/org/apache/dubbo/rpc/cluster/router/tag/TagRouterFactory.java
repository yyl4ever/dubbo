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
package org.apache.dubbo.rpc.cluster.router.tag;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.cluster.CacheableRouterFactory;
import org.apache.dubbo.rpc.cluster.Router;

/**
 * Tag router factory
 * 通过继承 CacheableRouterFactory 这个抽象类，间接实现了 RouterFactory 接口。
 *
 * 通过 TagRouter，我们可以将某一个或多个 Provider 划分到同一分组，约束流量只在指定分组中流转，这样就可以轻松达到流量隔离的目的，从而支持灰度发布等场景。
 *
 * 目前，Dubbo 提供了动态和静态两种方式给 Provider 打标签，其中动态方式就是通过服务治理平台动态下发标签，静态方式就是在 XML 等静态配置中打标签。Consumer 端可以在 RpcContext 的 attachment 中添加 request.tag 附加属性，注意保存在 attachment 中的值将会在一次完整的远程调用中持续传递，我们只需要在起始调用时进行设置，就可以达到标签的持续传递。
 */
@Activate(order = 100)
public class TagRouterFactory extends CacheableRouterFactory {

    public static final String NAME = "tag";

    @Override
    protected Router createRouter(URL url) {
        return new TagRouter(url);
    }
}
