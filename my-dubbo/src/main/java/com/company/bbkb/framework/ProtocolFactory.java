package com.company.bbkb.framework;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 21:24
 * @Description: 一个接口下有多种实现，考虑使用工厂模式
 */
public class ProtocolFactory {
    /*public static Protocol getProtocol() {
        // 工厂模式 -- 扩展时仍需要修改代码
        // vm options : -DprotocolName = http
        String protocolName = System.getProperty("protocolName");
        if (StringUtils.isBlank(protocolName)) {
            protocolName = "http";
        }
        switch (protocolName) {
            case "http" :
                return new HttpProtocol();
            // case "dubbo":
            // return new DubboProtocol();
            // 新加协议时，仍需要修改代码 -- 考虑 SPI 来解决
            default:
                break;
        }
        return new HttpProtocol();
    }*/

    public static Protocol getProtocol() {
        // java spi -- mysql 驱动就是用的该机制。
        // 弊端：当实现类有多个时，这里不太好取指定的实现类。2.没有依赖注入的功能 3.没有aop功能
        ServiceLoader<Protocol> serviceLoader = ServiceLoader.load(Protocol.class);
        Iterator<Protocol> iterator = serviceLoader.iterator();
        /*while(iterator.hasNext()){

        }*/
        return iterator.next();
    }
}
