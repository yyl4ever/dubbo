package com.company.bbkb.framework;

import java.io.Serializable;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 20:09
 * @Description: 参数对象(需要通过网络传输)
 */
public class Invocation implements Serializable {
    private static final long serialVersionUID = -1548197584418986937L;

    private String interfaceName;
    private String methodName;
    /**
     * 参数类型列表
     */
    private Class[] paramTypes;

    private Object[] params;

    public Invocation(String interfaceName, String methodName, Class[] paramTypes, Object[] params) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.params = params;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class[] getParamTypes() {
        return paramTypes;
    }

    public void setParamTypes(Class[] paramTypes) {
        this.paramTypes = paramTypes;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }
}
