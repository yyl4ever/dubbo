package com.company.bbkb.spi.service;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @Author: yangyl
 * @Date: 2020-05-03 11:11
 * @Description: 模拟 IOC 注入
 */
@SPI
public interface SpringCar {
    /**
     * 从URL中获取 carType 对应的 value,拿到 value 相应的实现类
     * @param url
     * @return
     */
    @Adaptive(value = "carType")
    String getColor(URL url);
}
