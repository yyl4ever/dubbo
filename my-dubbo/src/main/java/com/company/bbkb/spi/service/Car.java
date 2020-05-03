package com.company.bbkb.spi.service;

import org.apache.dubbo.common.extension.SPI;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:11
 * @Description:
 */
@SPI
public interface Car {
    String getColor();
}
