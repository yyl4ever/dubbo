package com.company.bbkb.framework;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 21:13
 * @Description:为避免不同协议切换带来的修改
 */
public interface Protocol {
    // 将多个类似的东西抽象出共通的部分

    /**
     * 服务提供方启动
     * @param url
     */
    void start(URL url);


    /**
     * 服务消费方发送请求
     * @param url
     * @param invocation
     * @return
     */
    String send(URL url, Invocation invocation);
}
