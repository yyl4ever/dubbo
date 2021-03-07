package com.company.bbkb.provider;

import com.company.bbkb.framework.Protocol;
import com.company.bbkb.framework.ProtocolFactory;
import com.company.bbkb.framework.URL;
import com.company.bbkb.protocol.http.HttpProtocol;
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
        // 一般是 {服务名称:List<URL>--集群}
        URL url = new URL("localhost", 8080);
        RemoteMapRegister.register(IHelloService.class.getName(), url);

        // 3. 启动 tomcat，在由 http 协议切换到 dubbo 协议(netty实现)这里需要手动修改，很麻烦
        // HttpServer httpServer = new HttpServer();
        // httpServer.start("localhost", 8080);

        // 启动 tomcat. 一个接口多个实现，为避免切换时修改代码，需要更改为工厂模式
        /*Protocol httpProtocol = new HttpProtocol();
        httpProtocol.start(url);*/

        Protocol protocol = ProtocolFactory.getProtocol();
        protocol.start(url);
    }
}
