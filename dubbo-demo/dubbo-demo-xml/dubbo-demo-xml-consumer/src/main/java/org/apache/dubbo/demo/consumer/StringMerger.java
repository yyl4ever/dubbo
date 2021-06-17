package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.rpc.cluster.Merger;

/**
 * @Author: yangyl
 * @Date: 2021-06-10 18:43
 * @Description:
 */
public class StringMerger implements Merger<String> {
    @Override
    public String merge(String... items) {
        if (ArrayUtils.isEmpty(items)) {
            // 检测空返回值
            return "";
        }
        String result = "";
        for (String item : items) {
            // 通过竖线将多个Provider的返回值拼接起来
            result += item + "|";
        }
        return result;
    }
}
