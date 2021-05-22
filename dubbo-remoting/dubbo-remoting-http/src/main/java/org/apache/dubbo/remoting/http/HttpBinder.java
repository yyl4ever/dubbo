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
package org.apache.dubbo.remoting.http;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.Constants;

/**
 * HttpBinder
 * dubbo-remoting-http 模块的入口是 HttpBinder 接口，它被 @SPI 注解修饰，是一个扩展接口，有三个扩展实现，默认使用的是 JettyHttpBinder 实现
 */
@SPI("jetty")
public interface HttpBinder {

    /**
     * bind the server.
     *
     * @param url server url.
     * @return server.
     * 根据 URL 的 server 参数选择相应的 HttpBinder 扩展实现，不同 HttpBinder 实现返回相应的 HttpServer 实现。
     */
    @Adaptive({Constants.SERVER_KEY})
    HttpServer bind(URL url, HttpHandler handler);

}
