package com.company.bbkb.protocol.http;

import com.company.bbkb.framework.Invocation;
import com.company.bbkb.provider.LocalRegister;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:55
 * @Description:
 */
public class HttpServerHandler {

    /**
     * 服务提供方处理请求，返回结果
     * @param req
     * @param resp
     */
    public void handler(HttpServletRequest req, HttpServletResponse resp) {
        try {
            InputStream inputStream = req.getInputStream();
            ObjectInputStream ois = new ObjectInputStream(inputStream);

            Invocation invocation = (Invocation) ois.readObject();

            // 服务提供方从本地注册中获取实现类
            Class implClass = LocalRegister.get(invocation.getInterfaceName());
            Method method = implClass.getMethod(invocation.getMethodName(), invocation.getParamTypes());


            //String result = (String)method.invoke(implClass.newInstance(), invocation.getParams());
            String result = (String)method.invoke(implClass.newInstance(), invocation.getParams());

            // 将结果发送给消费方
            IOUtils.write(result, resp.getOutputStream());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
