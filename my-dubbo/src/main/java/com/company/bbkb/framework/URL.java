package com.company.bbkb.framework;

import java.io.Serializable;
import java.util.Objects;

/**
 * @Author: yangyl
 * @Date: 2020-05-02 19:11
 * @Description:
 */
public class URL implements Serializable {
    private static final long serialVersionUID = -1241481746370487138L;

    private String hostname;
    private Integer port;

    public URL(String hostname, Integer port) {
        this.hostname = hostname;
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        URL url = (URL) o;
        return hostname.equals(url.hostname) &&
                port.equals(url.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, port);
    }
}
