package com.company.bbkb.protocol.http;

import com.company.bbkb.framework.Invocation;
import com.company.bbkb.framework.Protocol;
import com.company.bbkb.framework.URL;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 21:16
 * @Description:
 */
public class HttpProtocol implements Protocol {
    @Override
    public void start(URL url) {
        new HttpServer().start(url.getHostname(), url.getPort());
    }

    @Override
    public String send(URL url, Invocation invocation) {
        return new HttpClient().send(url.getHostname(), url.getPort(), invocation);
    }
}
