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
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.PojoUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

import com.alibaba.fastjson.JSON;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.rpc.Constants.FAIL_PREFIX;
import static org.apache.dubbo.rpc.Constants.FORCE_PREFIX;
import static org.apache.dubbo.rpc.Constants.MOCK_KEY;
import static org.apache.dubbo.rpc.Constants.RETURN_KEY;
import static org.apache.dubbo.rpc.Constants.RETURN_PREFIX;
import static org.apache.dubbo.rpc.Constants.THROW_PREFIX;

final public class MockInvoker<T> implements Invoker<T> {
    private final static ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private final static Map<String, Invoker<?>> MOCK_MAP = new ConcurrentHashMap<String, Invoker<?>>();
    private final static Map<String, Throwable> THROWABLE_MAP = new ConcurrentHashMap<String, Throwable>();

    private final URL url;
    private final Class<T> type;

    public MockInvoker(URL url, Class<T> type) {
        this.url = url;
        this.type = type;
    }

    public static Object parseMockValue(String mock) throws Exception {
        return parseMockValue(mock, null);
    }

    public static Object parseMockValue(String mock, Type[] returnTypes) throws Exception {
        Object value = null;
        if ("empty".equals(mock)) {
            value = ReflectUtils.getEmptyObject(returnTypes != null && returnTypes.length > 0 ? (Class<?>) returnTypes[0] : null);
        } else if ("null".equals(mock)) {
            value = null;
        } else if ("true".equals(mock)) {
            value = true;
        } else if ("false".equals(mock)) {
            value = false;
        } else if (mock.length() >= 2 && (mock.startsWith("\"") && mock.endsWith("\"")
                || mock.startsWith("\'") && mock.endsWith("\'"))) {
            value = mock.subSequence(1, mock.length() - 1);
        } else if (returnTypes != null && returnTypes.length > 0 && returnTypes[0] == String.class) {
            value = mock;
        } else if (StringUtils.isNumeric(mock, false)) {
            value = JSON.parse(mock);
        } else if (mock.startsWith("{")) {
            value = JSON.parseObject(mock, Map.class);
        } else if (mock.startsWith("[")) {
            value = JSON.parseObject(mock, List.class);
        } else {
            value = mock;
        }
        if (ArrayUtils.isNotEmpty(returnTypes)) {
            value = PojoUtils.realize(value, (Class<?>) returnTypes[0], returnTypes.length > 1 ? returnTypes[1] : null);
        }
        return value;
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        if (invocation instanceof RpcInvocation) {
            ((RpcInvocation) invocation).setInvoker(this);
        }
        // 获取mock值(会从URL中的methodName.mock参数或mock参数获取)
        String mock = null;
        if (getUrl().hasMethodParameter(invocation.getMethodName())) {
            mock = getUrl().getParameter(invocation.getMethodName() + "." + MOCK_KEY);
        }
        if (StringUtils.isBlank(mock)) {
            mock = getUrl().getParameter(MOCK_KEY);
        }
        // 没有配置mock值，直接抛出异常 -- 因为已经进入到了 MockInvoker
        if (StringUtils.isBlank(mock)) {
            throw new RpcException(new IllegalAccessException("mock can not be null. url :" + url));
        }
        // mock值进行处理，去除"force:"、"fail:"前缀等
        // normalize -- 标准化
        mock = normalizeMock(URL.decode(mock));
        if (mock.startsWith(RETURN_PREFIX)) {
            // mock 参数以 return 开头：直接返回 mock 参数指定的固定值，例如，empty、null、true、false、json 等。
            mock = mock.substring(RETURN_PREFIX.length()).trim();
            try {
                // 获取响应结果的类型
                Type[] returnTypes = RpcUtils.getReturnTypes(invocation);
                // 根据结果类型，对mock值中结果值进行转换
                // mock 参数中指定的固定返回值将会由 parseMockValue() 方法进行解析。
                Object value = parseMockValue(mock, returnTypes);
                // 将固定的mock值设置到Result中
                return AsyncRpcResult.newDefaultAsyncResult(value, invocation);
            } catch (Exception ew) {
                throw new RpcException("mock return invoke error. method :" + invocation.getMethodName()
                        + ", mock:" + mock + ", url: " + url, ew);
            }
        } else if (mock.startsWith(THROW_PREFIX)) {
            // mock 参数以 throw 开头：直接抛出异常。如果在 mock 参数中没有指定异常类型，则抛出 RpcException，否则抛出指定的 Exception 类型。
            mock = mock.substring(THROW_PREFIX.length()).trim();
            if (StringUtils.isBlank(mock)) {
                // 未指定异常类型，直接抛出RpcException
                throw new RpcException("mocked exception for service degradation.");
            } else { // user customized class
                // 抛出自定义异常
                Throwable t = getThrowable(mock);
                throw new RpcException(RpcException.BIZ_EXCEPTION, t);
            }
        } else { //impl mock
            // mock 参数为 true 或 default 时，会查找服务接口对应的 Mock 实现；如果是其他值，则直接作为服务接口的 Mock 实现。拿到 Mock 实现之后，转换成 Invoker 进行调用。
            try {
                // 执行mockService得到mock结果
                Invoker<T> invoker = getInvoker(mock);
                return invoker.invoke(invocation);
            } catch (Throwable t) {
                throw new RpcException("Failed to create mock implementation class " + mock, t);
            }
        }
    }

    public static Throwable getThrowable(String throwstr) {
        Throwable throwable = THROWABLE_MAP.get(throwstr);
        if (throwable != null) {
            return throwable;
        }

        try {
            Throwable t;
            Class<?> bizException = ReflectUtils.forName(throwstr);
            Constructor<?> constructor;
            // how to use in work?
            constructor = ReflectUtils.findConstructor(bizException, String.class);
            t = (Throwable) constructor.newInstance(new Object[]{"mocked exception for service degradation."});
            // 为啥限制大小？
            if (THROWABLE_MAP.size() < 1000) {
                THROWABLE_MAP.put(throwstr, t);
            }
            return t;
        } catch (Exception e) {
            throw new RpcException("mock throw error :" + throwstr + " argument error.", e);
        }
    }

    /**
     * 处理 MOCK_MAP 缓存的读写、Mock 实现类的查找、生成和调用 Invoker
     * @param mockService
     * @return
     */
    @SuppressWarnings("unchecked")
    private Invoker<T> getInvoker(String mockService) {
        // 尝试从MOCK_MAP集合中获取对应的Invoker对象
        //  为毛要强转？定义的是Invoker<?>
        Invoker<T> invoker = (Invoker<T>) MOCK_MAP.get(mockService);
        if (invoker != null) {
            return invoker;
        }
        // 根据serviceType查找mock的实现类
        Class<T> serviceType = (Class<T>) ReflectUtils.forName(url.getServiceInterface());
        T mockObject = (T) getMockObject(mockService, serviceType);
        // 写入缓存
        invoker = PROXY_FACTORY.getInvoker(mockObject, serviceType, url);
        // 如果数量过多，就不缓存了
        if (MOCK_MAP.size() < 10000) {
            // 写入缓存
            MOCK_MAP.put(mockService, invoker);
        }
        return invoker;
    }

    /**
     * 在 getMockObject() 方法中会检查 mockService 参数是否为 true 或 default，
     * 如果是的话，则在服务接口后添加 Mock 字符串，作为服务接口的 Mock 实现；如果不是的话，则直接将 mockService 实现作为服务接口的 Mock 实现。
     * @param mockService
     * @param serviceType
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Object getMockObject(String mockService, Class serviceType) {
        if (ConfigUtils.isDefault(mockService)) {
            // 如果mock为true或default值，会在服务接口后添加Mock字符串，得到对应的实现类名称，并进行实例化
            mockService = serviceType.getName() + "Mock";
        }

        Class<?> mockClass = ReflectUtils.forName(mockService);
        if (!serviceType.isAssignableFrom(mockClass)) {
            //  检查mockClass是否继承serviceType接口
            throw new IllegalStateException("The mock class " + mockClass.getName() +
                    " not implement interface " + serviceType.getName());
        }

        try {
            return mockClass.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("No default constructor from mock class " + mockClass.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Normalize mock string:
     *
     * <ol>
     * <li>return => return null</li>
     * <li>fail => default</li>
     * <li>force => default</li>
     * <li>fail:throw/return foo => throw/return foo</li>
     * <li>force:throw/return foo => throw/return foo</li>
     * </ol>
     *
     * @param mock mock string
     * @return normalized mock string
     */
    public static String normalizeMock(String mock) {
        if (mock == null) {
            return mock;
        }

        mock = mock.trim();

        if (mock.length() == 0) {
            return mock;
        }

        if (RETURN_KEY.equalsIgnoreCase(mock)) {
            return RETURN_PREFIX + "null";
        }

        if (ConfigUtils.isDefault(mock) || "fail".equalsIgnoreCase(mock) || "force".equalsIgnoreCase(mock)) {
            return "default";
        }

        if (mock.startsWith(FAIL_PREFIX)) {
            mock = mock.substring(FAIL_PREFIX.length()).trim();
        }

        if (mock.startsWith(FORCE_PREFIX)) {
            mock = mock.substring(FORCE_PREFIX.length()).trim();
        }

        if (mock.startsWith(RETURN_PREFIX) || mock.startsWith(THROW_PREFIX)) {
            mock = mock.replace('`', '"');
        }

        return mock;
    }

    @Override
    public URL getUrl() {
        return this.url;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void destroy() {
        //do nothing
    }

    @Override
    public Class<T> getInterface() {
        return type;
    }
}
