package com.company.bbkb.register;

import com.company.bbkb.framework.URL;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;

import java.io.*;
import java.util.*;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:12
 * @Description: 模拟远程注册中心,实际一般用 zk，redis， nacos
 */
public class RemoteMapRegister {
    public static Map<String, List<URL>> REGISTER = new HashMap<>();

    /**
     * 注册,<服务名:List<URL>>
     * @param interfaceName
     * @param url
     */
    public static void register(String interfaceName, URL url) {
        List<URL> urls = REGISTER.get(interfaceName);
        // todo 有并发的问题
        if (CollectionUtils.isEmpty(urls)) {
            urls = Lists.newArrayList(url);
            REGISTER.put(interfaceName, urls);
        } else {
            urls.add(url);
        }

        saveFile();
    }



    public static URL random(String interfaceName) {
         REGISTER = getFile();

        List<URL> urls = REGISTER.get(interfaceName);
        Random random = new Random();
        int i = random.nextInt(urls.size());
        return urls.get(i);
    }

    private static Map<String, List<URL>> getFile() {
        try {
            FileInputStream fis = new FileInputStream("./tmp.txt");
            ObjectInputStream ois = new ObjectInputStream(fis);
            return (Map<String, List<URL>>) ois.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void saveFile() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream("./tmp.txt");
            ObjectOutputStream oos = new ObjectOutputStream(fileOutputStream);
            // REGISTER 内的元素都要序列化才能输出到文件流
            oos.writeObject(REGISTER);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
