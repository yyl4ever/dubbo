package com.company.bbkb.provider;

import com.company.bbkb.framework.URL;
import com.company.bbkb.protocol.http.HttpServer;
import com.company.bbkb.provider.service.IHelloService;
import com.company.bbkb.provider.service.impl.HelloServiceImpl;
import com.company.bbkb.register.RemoteMapRegister;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:33
 * @Description: 服务提供者启动类
 */
public class Provider {
    public static void main(String[] args) {
        // 1. 本地注册
        // {服务名称:实现类}
        LocalRegister.register(IHelloService.class.getName(), HelloServiceImpl.class);

        // 2. 远程注册
        // {服务名称:List<URL>--集群}
        URL url = new URL("localhost", 8080);
        RemoteMapRegister.register(IHelloService.class.getName(), url);

        // 3. 启动 tomcat
        HttpServer httpServer = new HttpServer();
        httpServer.start("localhost", 8080);
    }
}
