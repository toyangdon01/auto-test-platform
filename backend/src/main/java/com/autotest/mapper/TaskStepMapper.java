package com.autotest.mapper;

import com.autotest.entity.TaskStep;
import com.autotest.handler.JsonbTypeHandler;
import com.autotest.handler.JsonbListTypeHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 任务步骤 Mapper
 */
@Mapper
public interface TaskStepMapper extends BaseMapper<TaskStep> {
    
    /**
     * 根据任务ID查询所有步骤
     */
    @Results(id = "taskStepResultMap", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "taskId", column = "task_id"),
        @Result(property = "serverId", column = "server_id"),
        @Result(property = "stepName", column = "step_name"),
        @Result(property = "displayName", column = "display_name"),
        @Result(property = "script", column = "script"),
        @Result(property = "dependsOn", column = "depends_on"),
        @Result(property = "params", column = "params", typeHandler = JsonbTypeHandler.class),
        @Result(property = "status", column = "status"),
        @Result(property = "waitReason", column = "wait_reason"),
        @Result(property = "startedAt", column = "started_at"),
        @Result(property = "finishedAt", column = "finished_at"),
        @Result(property = "exitCode", column = "exit_code"),
        @Result(property = "output", column = "output"),
        @Result(property = "errorMessage", column = "error_message"),
        @Result(property = "resultCollector", column = "result_collector"),
        @Result(property = "parsedResult", column = "parsed_result", typeHandler = JsonbTypeHandler.class),
        @Result(property = "startupProbe", column = "startup_probe", typeHandler = JsonbTypeHandler.class),
        @Result(property = "probeStatus", column = "probe_status"),
        @Result(property = "probeStartedAt", column = "probe_started_at"),
        @Result(property = "probeFinishedAt", column = "probe_finished_at"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "outputFiles", column = "output_files", typeHandler = JsonbListTypeHandler.class),
        @Result(property = "serverName", column = "server_name"),
        @Result(property = "serverHost", column = "server_host")
    })
    @Select("SELECT ts.*, s.name as server_name, s.host as server_host " +
            "FROM task_steps ts " +
            "LEFT JOIN servers s ON ts.server_id = s.id " +
            "WHERE ts.task_id = #{taskId} AND ts.step_name != '_meta' " +
            "ORDER BY ts.id")
    List<TaskStep> findByTaskIdWithServer(@Param("taskId") Long taskId);
    
    /**
     * 根据任务和步骤名称查询
     */
    @ResultMap("taskStepResultMap")
    @Select("SELECT ts.*, s.name as server_name, s.host as server_host " +
            "FROM task_steps ts " +
            "LEFT JOIN servers s ON ts.server_id = s.id " +
            "WHERE ts.task_id = #{taskId} AND ts.step_name = #{stepName}")
    TaskStep findByTaskAndStepName(@Param("taskId") Long taskId, @Param("stepName") String stepName);
    
    /**
     * 统计任务步骤状态
     */
    @Select("SELECT status, COUNT(*) as count FROM task_steps WHERE task_id = #{taskId} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("taskId") Long taskId);
    
    /**
     * 根据ID查询步骤（使用 ResultMap 正确处理 JSONB 字段）
     */
    @ResultMap("taskStepResultMap")
    @Select("SELECT ts.*, s.name as server_name, s.host as server_host " +
            "FROM task_steps ts " +
            "LEFT JOIN servers s ON ts.server_id = s.id " +
            "WHERE ts.id = #{id}")
    TaskStep findByIdWithServer(@Param("id") Long id);
}
