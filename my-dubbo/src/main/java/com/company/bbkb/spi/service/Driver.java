package com.company.bbkb.spi.service;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:29
 * @Description:
 */
@SPI
public interface Driver {
    @Adaptive(value = "car")
    void driveCar(URL url);
}
