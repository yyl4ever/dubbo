package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;
import com.company.bbkb.spi.service.Driver;
import com.company.bbkb.spi.service.SpringCar;
import org.apache.dubbo.common.URL;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:30
 * @Description: SPI 实现依赖注入
 */
public class Trucker implements Driver {

    private SpringCar springCar;

    public void setSpringCar(SpringCar springCar) {
        this.springCar = springCar;
    }

    /**
     * 根据URL来获取要注入的实例
     * @param url
     */
    @Override
    public void driveCar(URL url) {
        System.out.println("I'm driving car of " + springCar.getColor(url));
    }
}
