package com.company.bbkb.protocol.http;

import com.company.bbkb.framework.Invocation;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 20:08
 * @Description:
 */
public class HttpClient {
    /**
     * 消费方发送请求参数
     * @param hostname
     * @param port
     * @param invocation
     * @return
     */
    public String send(String hostname, Integer port, Invocation invocation) {

        try {
            URL url = new URL("http", hostname, port, "/");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);

            // 对象通过输出流发送出去
            OutputStream outputStream = urlConnection.getOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(outputStream);

            oos.writeObject(invocation);
            oos.flush();
            oos.close();

            // 接收服务提供方的返回结果
            InputStream inputStream = urlConnection.getInputStream();
            String result = IOUtils.toString(inputStream);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
