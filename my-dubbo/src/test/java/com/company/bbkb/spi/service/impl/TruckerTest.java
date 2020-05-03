package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;
import com.company.bbkb.spi.service.Driver;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 12:37
 * @Description:
 */
public class TruckerTest {

    @Test
    public void driveCar() {
        // 每个接口对应一个 ExtensionLoader
        ExtensionLoader<Driver> extensionLoader = ExtensionLoader.getExtensionLoader(Driver.class);
        // 得到具体的实例
        Driver trucker = extensionLoader.getExtension("trucker");

        // 测试依赖注入
        Map<String, String> map = new HashMap<>();
        map.put("carType", "black");

        URL url = new URL("", "", 0, map);
        trucker.driveCar(url);
    }
}