package com.autotest.service;

import com.autotest.entity.Task;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * applyUserStepParams 单元测试：发起任务时填写的步骤参数必须压过脚本定义中的 defaultValue
 */
class TaskExecutionServiceStepParamsTest {

    private final TaskExecutionService service =
            Mockito.mock(TaskExecutionService.class, Mockito.CALLS_REAL_METHODS);

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> params, Task task, String stepName) throws Exception {
        Method method = TaskExecutionService.class.getDeclaredMethod(
                "applyUserStepParams", Map.class, Task.class, String.class);
        method.setAccessible(true);
        method.invoke(service, params, task, stepName);
    }

    private Task taskWithStepParams(String stepName, Map<String, Object> values) {
        Task task = new Task();
        Map<String, Map<String, Object>> stepParams = new LinkedHashMap<>();
        stepParams.put(stepName, values);
        task.setStepParams(stepParams);
        return task;
    }

    @Test
    void userValueOverridesStepDefault() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("threads", 8);
        Task task = taskWithStepParams("step_2", new LinkedHashMap<>(Map.of("threads", 32)));

        apply(params, task, "step_2");

        assertEquals(32, params.get("threads"));
    }

    @Test
    void stepDefaultKeptWhenUserOmitsParam() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("threads", 8);
        params.put("time_limit", 60);
        Task task = taskWithStepParams("step_2", new LinkedHashMap<>(Map.of("threads", 32)));

        apply(params, task, "step_2");

        assertEquals(32, params.get("threads"));
        assertEquals(60, params.get("time_limit"));
    }

    @Test
    void clearedInputIsExportedAsEmptyInsteadOfFallingBackToDefault() throws Exception {
        Map<String, Object> userParams = new LinkedHashMap<>();
        userParams.put("extra_options", "");
        Map<String, Object> params = new HashMap<>();
        params.put("extra_options", "--skip-trx=on");
        Task task = taskWithStepParams("step_2", userParams);

        apply(params, task, "step_2");

        assertTrue(params.containsKey("extra_options"));
        assertEquals("", params.get("extra_options"));
    }

    @Test
    void nullValueKeepsStepDefault() throws Exception {
        Map<String, Object> userParams = new LinkedHashMap<>();
        userParams.put("tables", null);
        Map<String, Object> params = new HashMap<>();
        params.put("tables", 4);
        Task task = taskWithStepParams("step_2", userParams);

        apply(params, task, "step_2");

        assertEquals(4, params.get("tables"));
    }

    @Test
    void backgroundFlagIsNotExported() throws Exception {
        Map<String, Object> userParams = new LinkedHashMap<>();
        userParams.put("_BACKGROUND", true);
        userParams.put("db_driver", "pgsql");
        Map<String, Object> params = new HashMap<>();
        params.put("_BACKGROUND", false);
        Task task = taskWithStepParams("step_2", userParams);

        apply(params, task, "step_2");

        assertEquals("pgsql", params.get("db_driver"));
        assertEquals(false, params.get("_BACKGROUND"));
    }

    @Test
    void missingStepEntryLeavesParamsUnchanged() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("threads", 8);
        Task task = taskWithStepParams("step_1", new LinkedHashMap<>(Map.of("threads", 32)));

        apply(params, task, "step_2");

        assertEquals(8, params.get("threads"));
        assertFalse(params.containsKey("db_driver"));
    }

    @Test
    void nullStepParamsLeavesParamsUnchanged() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("threads", 8);
        Task task = new Task();
        task.setStepParams(null);

        apply(params, task, "step_2");

        assertEquals(8, params.get("threads"));
    }
}
