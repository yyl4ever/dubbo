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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.transport.dispatcher.all.AllDispatcher;

/**
 * ChannelHandlerWrapper (SPI, Singleton, ThreadSafe)
 * 每个 WrappedChannelHandler 实现类的对象都由一个相应的 Dispatcher 实现类创建
 * https://mmbiz.qpic.cn/mmbiz_png/lnCqjsQ6QHc3kY05R0DDoLSiaQXu9K67BXunMwuJck9uceEoSsVCXwBR1tTzwRCsvDNjsUZTciaLRSJicuBEXnuCA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1
 */
@SPI(AllDispatcher.NAME)// 默认扩展名是all
public interface Dispatcher {

    /**
     * dispatch the message to threadpool.
     *
     * @param handler
     * @param url
     * @return channel handler
     */
    // 通过URL中的参数可以指定扩展名，覆盖默认扩展名
    @Adaptive({Constants.DISPATCHER_KEY, "dispather", "channel.handler"})
    // The last two parameters are reserved for compatibility with the old configuration
    ChannelHandler dispatch(ChannelHandler handler, URL url);

}
