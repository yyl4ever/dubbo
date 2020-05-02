package com.company.bbkb.provider.service.impl;

import com.company.bbkb.provider.service.IHelloService;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:32
 * @Description:
 */
public class HelloServiceImpl implements IHelloService {
    private static final String HELLO = "hello";
    @Override
    public String sayHello(String name) {
        return HELLO + " " + name;
    }
}
