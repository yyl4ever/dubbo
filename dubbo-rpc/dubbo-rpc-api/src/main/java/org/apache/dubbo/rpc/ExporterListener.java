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
 * ExporterListener. (SPI, Singleton, ThreadSafe)
 *///为了监听服务发布事件以及取消暴露事件，Dubbo 定义了一个 SPI 扩展接口
@SPI
public interface ExporterListener {

    /**
     * The exporter exported.
     *
     * @param exporter
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Protocol#export(Invoker)
     */// 当有服务发布的时候，会触发该方法
    void exported(Exporter<?> exporter) throws RpcException;

    /**
     * The exporter unexported.
     *
     * @param exporter
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Exporter#unexport()
     */// 当有服务取消发布的时候，会触发该方法
    void unexported(Exporter<?> exporter);

}