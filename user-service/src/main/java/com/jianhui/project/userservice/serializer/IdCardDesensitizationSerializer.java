package com.jianhui.project.userservice.serializer;

import cn.hutool.core.util.DesensitizedUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 身份证号脱敏序列化器
 */
public class IdCardDesensitizationSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String idNumber, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
//        hutool工具类脱敏
        String s = DesensitizedUtil.idCardNum(idNumber, 4, 4);
        jsonGenerator.writeString(s);
    }
}
