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
package org.apache.dubbo.rpc.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.AbstractProtocol;

/**
 * MockProtocol is used for generating a mock invoker by URL and type on consumer side
 * Protocol 接口的扩展实现，扩展名称为 mock。MockProtocol 只能通过 refer() 方法创建 MockInvoker，不能通过 export() 方法暴露服务
 */
final public class MockProtocol extends AbstractProtocol {

    @Override
    public int getDefaultPort() {
        return 0;
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        // 直接抛出异常，无法暴露服务
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Invoker<T> protocolBindingRefer(Class<T> type, URL url) throws RpcException {
        // 直接创建MockInvoker对象
        return new MockInvoker<>(url, type);
    }
}
