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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.extension.SPI;

/**
 * InvokerListener. (SPI, Singleton, ThreadSafe)
 * 监听 Consumer 引用服务时触发的事件
 */
@SPI
public interface InvokerListener {

    /**
     * The invoker referred
     *
     * @param invoker
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Protocol#refer(Class, org.apache.dubbo.common.URL)
     */// 当服务引用的时候，会触发该方法
    void referred(Invoker<?> invoker) throws RpcException;

    /**
     * The invoker destroyed.
     *
     * @param invoker
     * @see org.apache.dubbo.rpc.Invoker#destroy()
     */// 当销毁引用的服务时，会触发该方法
    void destroyed(Invoker<?> invoker);

}