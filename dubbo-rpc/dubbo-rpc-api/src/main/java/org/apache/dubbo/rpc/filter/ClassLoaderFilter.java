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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * Set the current execution thread class loader to service interface's class loader.
 * Provider 端的一个 Filter 实现，主要功能是切换类加载器 -- todo 为什么要去切换？
 */
@Activate(group = CommonConstants.PROVIDER, order = -30000)
public class ClassLoaderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 获取当前线程关联的 contextClassLoader
        ClassLoader ocl = Thread.currentThread().getContextClassLoader();
        // 加载服务接口类的类加载器 -- // 更新当前线程绑定的ClassLoader
        Thread.currentThread().setContextClassLoader(invoker.getInterface().getClassLoader());
        try {
            // 执行后续的 Filter 逻辑以及业务逻辑
            return invoker.invoke(invocation);
        } finally {
            // 将当前线程关联的 contextClassLoader 重置为原来的 contextClassLoader
            Thread.currentThread().setContextClassLoader(ocl);
        }
    }

}
