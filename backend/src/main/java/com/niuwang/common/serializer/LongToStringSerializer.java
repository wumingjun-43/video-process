package com.niuwang.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Long 类型序列化为字符串，避免前端 JS 精度丢失
 * 雪花算法 ID 超过 Number.MAX_SAFE_INTEGER (2^53-1)
 */
public class LongToStringSerializer extends JsonSerializer<Long> {
    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value != null) {
            gen.writeString(value.toString());
        } else {
            gen.writeNull();
        }
    }
}
