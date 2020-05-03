package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:12
 * @Description:
 */
public class BlackCar implements Car {
    @Override
    public String getColor() {
        return "BLACK";
    }
}
