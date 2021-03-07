package com.company.bbkb.framework;

import com.company.bbkb.register.RemoteMapRegister;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 20:34
 * @Description:
 */
public class ProxyFactory {
    /**
     * 消费方用代理类来执行方法
     * @param interfaceClass
     * @param <T>
     * @return
     */
    public static <T> T getProxy(Class interfaceClass) {
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[]{interfaceClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // 当要切换到 dubbo 协议(netty实现)，这里就需要手动修改，很麻烦
                //HttpClient httpClient = new HttpClient();
                //Invocation invocation = new Invocation(IHelloService.class.getName(), "sayHello", new Class[]{String.class}, new Object[]{"yyl"});

                // 这里地址是写死的，可用 URL url1 = RemoteMapRegister.random(interfaceClass.getName()); 代替
                //String result = httpClient.send("localhost", 8080, invocation);

                Protocol protocol = ProtocolFactory.getProtocol();
                //Protocol protocol = new HttpProtocol();
                Invocation invocation = new Invocation(interfaceClass.getName(), method.getName(), method.getParameterTypes(), args);

                URL url = RemoteMapRegister.random(interfaceClass.getName());
                String result = protocol.send(url, invocation);
                return result;
            }
        });
    }
}
