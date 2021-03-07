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
    // 依赖注入，其中 carType 是入参 URL 中内容的某个 key
    @Adaptive(value = "carType")
    // URL 是 dubbo 中的概念
    void driveCar(URL url);
}
