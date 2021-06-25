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

import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.CallbackServiceCodec.encodeInvocationArgument;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DECODE_IN_IO_THREAD_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_DECODE_IN_IO_THREAD;

/**
 * Dubbo codec.
 *  ExchangeCodec 只处理了 Dubbo 协议的请求头，而 DubboCodec 则是通过继承的方式，
 *  在 ExchangeCodec 基础之上，添加了解析 Dubbo 消息体的功能。
 */
public class DubboCodec extends ExchangeCodec {

    public static final String NAME = "dubbo";
    public static final String DUBBO_VERSION = Version.getProtocolVersion();
    // 异常返回
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    // 响应结果
    public static final byte RESPONSE_VALUE = 1;
    // 响应空值
    public static final byte RESPONSE_NULL_VALUE = 2;
    // 异常返回包含上下文信息
    public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    // 响应结果包含上下文信息
    public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    // 响应为空带有上下文信息
    public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Logger log = LoggerFactory.getLogger(DubboCodec.class);

    /**
     * 会根据 DECODE_IN_IO_THREAD_KEY 这个参数决定是否在 DubboCodec 中进行解码（DubboCodec 是在 IO 线程中调用的）
     * @param channel
     * @param is
     * @param header
     * @return
     * @throws IOException
     */
    @Override
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        // header -- dubbo 的 header，取出 header 第三个字节，然后与 -128 进行位运算，
        // 如果得到第 16 个 bit 位为0，通过协议可知，0代表响应报文。
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        // get request id. -- 这个 id 连接了请求和响应 -- 发起 request 请求的时候，从 request 对象中取出来请求编号写到协议里面的。
        long id = Bytes.bytes2long(header, 4);
        if ((flag & FLAG_REQUEST) == 0) {
            // 对响应报文解码
            // decode response.
            Response res = new Response(id);
            // 是判断当前数据包是不是一个心跳包
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // 第 4 个字节代表的是状态位
            // get status.
            byte status = header[3];
            res.setStatus(status);
            try {
                if (status == Response.OK) {
                    Object data;
                    if (res.isEvent()) {
                        ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                        data = decodeEventData(channel, in);
                    } else {
                        DecodeableRpcResult result;
                        // 判断到底在哪（IO线程/客户端线程池）进行响应解析
                        // DECODE_IN_IO_THREAD_KEY:控制是否在 IO 线程里面进行解码操作，默认 false
                        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            result.decode();// 直接调用decode()方法在当前IO线程中解码
                        } else {// 这里只是读取数据，不会调用decode()方法在当前IO线程中进行解码
                            /**
                             *  如果不在 DubboCodec 中解码，那会在哪里解码呢？
                             *  DecodeHandler（Transport 层），它的 received() 方法也是可以进行解码的，
                             *  另外，DecodeableRpcInvocation 中有一个 hasDecoded 字段来判断当前是否已经完成解码，
                             *  这样，三者配合就可以根据 DECODE_IN_IO_THREAD_KEY 参数决定执行解码操作的线程了。
                             */
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    res.setResult(data);
                } else {
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode response failed: " + t.getMessage(), t);
                }
                res.setStatus(Response.CLIENT_ERROR);
                res.setErrorMessage(StringUtils.toString(t));
            }
            return res;
        } else {
            // 对请求报文解码
            // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getProtocolVersion());
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                Object data;
                if (req.isEvent()) {
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    data = decodeEventData(channel, in);
                } else {
                    DecodeableRpcInvocation inv;
                    if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        inv.decode();
                    } else {
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true);
                req.setData(t);
            }

            return req;
        }
    }

    private byte[] readMessageData(InputStream is) throws IOException {
        if (is.available() > 0) {
            byte[] result = new byte[is.available()];
            is.read(result);
            return result;
        }
        return new byte[]{};
    }

    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeRequestData(channel, out, data, DUBBO_VERSION);
    }

    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeResponseData(channel, out, data, DUBBO_VERSION);
    }

    /**
     * 按照 Dubbo 协议的格式编码 Request 请求体
     * @param channel
     * @param out
     * @param data
     * @param version
     * @throws IOException
     */
    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        //// 请求体相关的内容，都封装在了RpcInvocation
        RpcInvocation inv = (RpcInvocation) data;

        out.writeUTF(version);// 写入版本号
        out.writeUTF(inv.getAttachment(PATH_KEY));
        out.writeUTF(inv.getAttachment(VERSION_KEY));

        out.writeUTF(inv.getMethodName());// 写入方法名称
        out.writeUTF(inv.getParameterTypesDesc());// 写入参数类型列表
        Object[] args = inv.getArguments();// 依次写入全部参数
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                out.writeObject(encodeInvocationArgument(channel, inv, i));
            }
        }
        // 依次写入全部的附加信息
        out.writeAttachments(inv.getObjectAttachments());
    }

    /**
     * 对响应数据做解码操作
     * @param channel
     * @param out
     * @param data
     * @param version
     * @throws IOException
     * 标号为①的地方是判断当前版本是否支持上下文信息传递。
     *
     * 标号为②的地方是判断是否是异常返回。
     *
     * 标号为③的地方表明不是异常返回，则判断返回值是否为 null。
     *
     * 标号为④的地方表明是正常返回，根据是否支持上下文信息传递，从而判断是只返回响应结果的还是既有响应结果，也有上下文信息的返回类型。
     *
     * 标号为⑤的地方表明是异常返回，根据是否支持上下文信息传递，从而判断是只返回异常结果的还是既有异常结果，也有上下文信息的返回类型。
     *
     */
    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        Result result = (Result) data;
        // 判断当前版本是否支持上下文信息传递
        // currently, the version value in Response records the version of Request
        boolean attach = Version.isSupportResponseAttachment(version);
        Throwable th = result.getException();
        if (th == null) {
            Object ret = result.getValue();
            if (ret == null) {
                out.writeByte(attach ? RESPONSE_NULL_VALUE_WITH_ATTACHMENTS : RESPONSE_NULL_VALUE);
            } else {
                out.writeByte(attach ? RESPONSE_VALUE_WITH_ATTACHMENTS : RESPONSE_VALUE);
                out.writeObject(ret);
            }
        } else {
            out.writeByte(attach ? RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS : RESPONSE_WITH_EXCEPTION);
            out.writeThrowable(th);
        }

        if (attach) {
            // returns current version of Response to consumer side.
            result.getObjectAttachments().put(DUBBO_VERSION_KEY, Version.getProtocolVersion());
            out.writeAttachments(result.getObjectAttachments());
        }
    }
}
