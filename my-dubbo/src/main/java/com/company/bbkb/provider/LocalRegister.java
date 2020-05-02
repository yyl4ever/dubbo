package com.company.bbkb.provider;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:32
 * @Description:
 */
public class LocalRegister {

    private static Map<String, Class> map = new HashMap<>();

    public static void register(String interfaceName, Class implClass) {
        // todo 有并发问题
        map.put(interfaceName, implClass);
    }

    public static Class get(String interfaceName) {
        return map.get(interfaceName);
    }
}
