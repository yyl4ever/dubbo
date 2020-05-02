package com.company.bbkb.consumer;

import com.company.bbkb.framework.Invocation;
import com.company.bbkb.framework.ProxyFactory;
import com.company.bbkb.protocol.http.HttpClient;
import com.company.bbkb.provider.service.IHelloService;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 20:08
 * @Description:
 */
public class Consumer {

    /*public static void main(String[] args) {
        HttpClient httpClient = new HttpClient();
        Invocation invocation = new Invocation(IHelloService.class.getName(), "sayHello", new Class[]{String.class}, new Object[]{"yyl"});
        String result = httpClient.send("localhost", 8080, invocation);
        System.out.println(result);
    }*/

    public static void main(String[] args) {
        IHelloService helloService = ProxyFactory.getProxy(IHelloService.class);
        String cx = helloService.sayHello("cx");
        System.out.println(cx);
    }
}
