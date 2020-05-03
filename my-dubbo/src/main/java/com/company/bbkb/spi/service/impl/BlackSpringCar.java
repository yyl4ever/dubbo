package com.company.bbkb.spi.service.impl;

import com.company.bbkb.spi.service.Car;
import com.company.bbkb.spi.service.SpringCar;
import org.apache.dubbo.common.URL;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:12
 * @Description:
 */
public class BlackSpringCar implements SpringCar {
    @Override
    public String getColor(URL url) {
        return "BLACK";
    }
}
