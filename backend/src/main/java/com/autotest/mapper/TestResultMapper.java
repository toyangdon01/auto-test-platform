package com.autotest.mapper;

import com.autotest.entity.TestResult;
import com.autotest.handler.JsonbTypeHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 测试结果 Mapper
 *
 * @author auto-test-platform
 */
@Mapper
public interface TestResultMapper extends BaseMapper<TestResult> {
    
    /**
     * 分页查询结果（带任务名称和脚本名称）
     */
    @Results(id = "testResultMap", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "task_server_id", property = "taskServerId"),
        @Result(column = "server_id", property = "serverId"),
        @Result(column = "result", property = "result"),
        @Result(column = "exit_code", property = "exitCode"),
        @Result(column = "raw_output", property = "rawOutput"),
        @Result(column = "parsed_data", property = "parsedData", typeHandler = JsonbTypeHandler.class),
        @Result(column = "started_at", property = "startedAt"),
        @Result(column = "finished_at", property = "finishedAt"),
        @Result(column = "duration_ms", property = "durationMs"),
        @Result(column = "task_name", property = "taskName"),
        @Result(column = "script_name", property = "scriptName"),
        @Result(column = "server_name", property = "serverName")
    })
    @Select("<script>" +
            "SELECT r.*, t.name as task_name, s.name as script_name, sv.name as server_name " +
            "FROM test_results r " +
            "LEFT JOIN tasks t ON r.task_id = t.id " +
            "LEFT JOIN scripts s ON t.script_id = s.id " +
            "LEFT JOIN servers sv ON r.server_id = sv.id " +
            "<where>" +
            "  <if test='taskId != null'>" +
            "    AND r.task_id = #{taskId}" +
            "  </if>" +
            "  <if test='result != null and result != \"\"'>" +
            "    AND r.result = #{result}" +
            "  </if>" +
            "  <if test='scriptId != null'>" +
            "    AND t.script_id = #{scriptId}" +
            "  </if>" +
            "</where>" +
            "ORDER BY r.id DESC" +
            "</script>")
    IPage<TestResult> selectPageWithNames(Page<TestResult> page,
                                           @Param("taskId") Long taskId,
                                           @Param("result") String result,
                                           @Param("scriptId") Long scriptId);
}
