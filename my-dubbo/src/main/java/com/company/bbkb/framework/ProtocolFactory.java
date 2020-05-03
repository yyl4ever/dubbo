package com.company.bbkb.framework;

import com.company.bbkb.protocol.http.HttpProtocol;
import org.apache.commons.lang.StringUtils;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 21:24
 * @Description:
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
            // case "dubbo": return new DubboProtocol();
            default:
                break;
        }
        return new HttpProtocol();
    }*/

    public static Protocol getProtocol() {
        // java spi
        ServiceLoader<Protocol> serviceLoader = ServiceLoader.load(Protocol.class);
        Iterator<Protocol> iterator = serviceLoader.iterator();
        /*while(iterator.hasNext()){

        }*/
        return iterator.next();
    }
}
