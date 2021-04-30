/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.serialize.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provide a unified serialization registry, this class used for {@code dubbo-serialization-fst}
 * and {@code dubbo-serialization-kryo}, it will register some classes at startup time (for example {@link AbstractKryoFactory#create})
 */
public abstract class SerializableClassRegistry {


    private static final Map<Class<?>, Object> REGISTRATIONS = new LinkedHashMap<>();

    /**
     * only supposed to be called at startup time
     *
     * @param clazz object type
     */
    public static void registerClass(Class<?> clazz) {
        registerClass(clazz, null);
    }

    /**
     * only supposed to be called at startup time
     *
     * @param clazz object type
     * @param serializer object serializer
     *                   将待优化的类写入该集合中暂存,在使用 Kryo、FST 等序列化算法时，会读取该集合中的类，完成注册操作
     */
    public static void registerClass(Class<?> clazz, Object serializer) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class registered to kryo cannot be null!");
        }
        REGISTRATIONS.put(clazz, serializer);
    }

    /**
     * get registered classes
     *
     * @return class serializer
     * 按照 Dubbo 官方文档的说法，即使不注册任何类进行优化，Kryo 和 FST 的性能依然普遍优于Hessian2 和 Dubbo 序列化。
     * */
    public static Map<Class<?>, Object> getRegisteredClasses() {
        return REGISTRATIONS;
    }
}
