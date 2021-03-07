package com.company.bbkb.register;

import com.company.bbkb.framework.URL;

import java.io.*;
import java.util.*;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:12
 * @Description: 模拟远程注册中心, 实际一般用 zk，redis， nacos
 */
public class RemoteMapRegister {
    private static Map<String, List<URL>> REGISTER = new HashMap<>();

    /**
     * 注册,<服务名:Set<URL>>
     *
     * @param interfaceName
     * @param url
     */
    public static void register(String interfaceName, URL url) {
        // 借鉴 dubbo 中相关代码的加锁思想，暂时处理并发问题
        List<URL> urls = REGISTER.get(interfaceName);
        if (urls == null) {
            synchronized (REGISTER) {
                urls = REGISTER.get(interfaceName);
                if (urls == null) {
                    urls = new ArrayList<>();
                    REGISTER.put(interfaceName, urls);
                }
            }
        }
        // 需要重写 URL 的 equals、hashCode 方法
        if (!urls.contains(url)) {
            urls.add(url);
        }

        // 用文件共享来存放 URL 地址，避免服务提供方进程启动加载了地址，但消费方进程启动却读取不到的问题
        // todo 对文件的操作最好用读写锁，由于是多 JVM 进程访问，还需要考虑分布式锁
        saveFile();
    }

    /**
     * 模拟负载均衡
     * @param interfaceName
     * @return
     */
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
            // REGISTER 内的元素都要序列化才能输出到文件流 -- 这就要求 Map 中存储的 URL 也要序列化
            oos.writeObject(REGISTER);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
