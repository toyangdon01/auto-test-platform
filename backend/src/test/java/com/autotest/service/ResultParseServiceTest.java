package com.autotest.service;

import com.autotest.entity.ResultRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * key_value 内置解析的取值边界：一行只取一组键值，值取到行尾，不许再抠数字
 */
class ResultParseServiceTest {

    private final ResultParseService service = new ResultParseService(new ObjectMapper(), null);

    private ResultRule builtin(String format) {
        ResultRule rule = new ResultRule();
        rule.setParserType("builtin");
        rule.setBuiltinFormat(format);
        return rule;
    }

    @Test
    void parseKeyValue_KeepsValuesVerbatimAndOnlyOnePairPerLine() {
        Map<String, Object> parsed = service.parse("duration_seconds=2\n"
                + "throughput_ops=1500\n"
                + "mode=fast rounds=1\n"
                + "# 注释行\n"
                + "note: 值里带冒号也行\n", builtin("key_value"));

        assertEquals("2", parsed.get("duration_seconds"), parsed.toString());
        assertEquals("1500", parsed.get("throughput_ops"), parsed.toString());
        // 以前这行会被解析成 mode=1（数字被从尾部抠出来），rounds 整键丢失
        assertEquals("fast rounds=1", parsed.get("mode"), parsed.toString());
        assertEquals("值里带冒号也行", parsed.get("note"), parsed.toString());
        assertEquals(4, parsed.size(), parsed.toString());
    }

    @Test
    void parseJson_ReturnsStructuredTypes() {
        Map<String, Object> parsed = service.parse("{\"score\": 91, \"unit\": \"分\"}", builtin("json"));
        assertEquals(91, parsed.get("score"), parsed.toString());
        assertEquals("分", parsed.get("unit"), parsed.toString());
    }

    @Test
    void unknownBuiltinFormat_FailsLoudly() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.parse("a=1", builtin("csv")));
        assertTrue(e.getMessage().contains("解析失败"), e.getMessage());
    }

    @Test
    void emptyInput_FailsInsteadOfReturningEmptyMap() {
        assertThrows(RuntimeException.class, () -> service.parse("", builtin("key_value")));
    }
}
