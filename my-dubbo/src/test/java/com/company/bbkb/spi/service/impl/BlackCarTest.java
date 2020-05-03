package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.Test;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:14
 * @Description:
 */
public class BlackCarTest {

    @Test
    public void getColor() {
        // 每个接口对应一个 ExtensionLoader
        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
        Car whiteCar = extensionLoader.getExtension("white");
        System.out.println(whiteCar.getColor());

        /*Car blackCar = extensionLoader.getExtension("black");
        System.out.println(blackCar.getColor());*/
    }
}