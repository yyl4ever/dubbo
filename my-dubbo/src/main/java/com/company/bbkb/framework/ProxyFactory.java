package com.company.bbkb.framework;

import com.company.bbkb.protocol.http.HttpClient;
import com.company.bbkb.provider.service.IHelloService;
import com.company.bbkb.register.RemoteMapRegister;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;

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
                HttpClient httpClient = new HttpClient();
                //Invocation invocation = new Invocation(IHelloService.class.getName(), "sayHello", new Class[]{String.class}, new Object[]{"yyl"});
                Invocation invocation = new Invocation(interfaceClass.getName(), method.getName(), method.getParameterTypes(), args);

                URL url = RemoteMapRegister.random(interfaceClass.getName());
                String result = httpClient.send(url.getHostname(), url.getPort(), invocation);
                //String result = httpClient.send("localhost", 8080, invocation);
                return result;
            }
        });
    }
}
