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

package org.apache.dubbo.rpc.protocol.dubbo;

/**
 *
 */
public interface Constants {

    String SHARE_CONNECTIONS_KEY = "shareconnections";

    /**
     * By default, a consumer JVM instance and a provider JVM instance share a long TCP connection (except when connections are set),
     * which can set the number of long TCP connections shared to avoid the bottleneck of sharing a single long TCP connection.
     */
    String DEFAULT_SHARE_CONNECTIONS = "1";
    /**
     * 在老的（2.7.5 版本之前）线程池模型中，当业务数据返回后，默认在 IO 线程上进行反序列化操作，
     * 如果配置了 decode.in.io 参数为 false（默认为 true），则延迟到独立的客户端线程池进行反序列化操作。
     */
    String DECODE_IN_IO_THREAD_KEY = "decode.in.io";

    /**
     * 2.7.5 版本以后，默认值从 true 变为 false
     * 2.7.5 版本之前，业务数据返回后，默认在 IO 线程里面进行反序列化的操作。而2.7.5 版本之后，默认是延迟到客户端线程池里面进行反序列化的操作。
     */
    boolean DEFAULT_DECODE_IN_IO_THREAD = false;

    /**
     * callback inst id
     */
    String CALLBACK_SERVICE_KEY = "callback.service.instid";

    String CALLBACK_SERVICE_PROXY_KEY = "callback.service.proxy";

    String IS_CALLBACK_SERVICE = "is_callback_service";

    /**
     * Invokers in channel's callback
     */
    String CHANNEL_CALLBACK_KEY = "channel.callback.invokers.key";

    /**
     * The initial state for lazy connection
     */
    String LAZY_CONNECT_INITIAL_STATE_KEY = "connect.lazy.initial.state";

    /**
     * The default value of lazy connection's initial state: true
     *
     * @see #LAZY_CONNECT_INITIAL_STATE_KEY
     */
    boolean DEFAULT_LAZY_CONNECT_INITIAL_STATE = true;

    String OPTIMIZER_KEY = "optimizer";

    String ON_CONNECT_KEY = "onconnect";

    String ON_DISCONNECT_KEY = "ondisconnect";

    String ASYNC_METHOD_INFO = "async-method-info";

}
