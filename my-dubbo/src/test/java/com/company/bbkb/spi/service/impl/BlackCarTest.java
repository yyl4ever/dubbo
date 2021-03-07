package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.Test;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:14
 * @Description: todo debug 跟踪下源码
 */
public class BlackCarTest {

    /**
     * 从该例看 dubbo 的源码
     */
    @Test
    public void getColor() {
        // 每个接口对应一个 ExtensionLoader
        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
        // 设置为 true，则获取接口上 @SPI 指定的默认的实现类
//        Car whiteCar = extensionLoader.getExtension("true");
        Car whiteCar = extensionLoader.getExtension("white");
        System.out.println(whiteCar.getColor());

        /*Car blackCar = extensionLoader.getExtension("black");
        System.out.println(blackCar.getColor());*/
    }
}