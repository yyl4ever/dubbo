package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:25
 * @Description: SPI 实现 AOP
 */
public class CarWrapper implements Car {
    private Car car;

    public CarWrapper(Car car) {
        this.car = car;
    }

    @Override
    public String getColor() {
        System.out.println("before...");
        return car.getColor();
    }
}
