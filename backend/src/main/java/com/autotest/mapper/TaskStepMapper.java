package com.autotest.mapper;

import com.autotest.entity.TaskStep;
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
    @Select("SELECT ts.*, s.name as server_name, s.host as server_host " +
            "FROM task_steps ts " +
            "LEFT JOIN servers s ON ts.server_id = s.id " +
            "WHERE ts.task_id = #{taskId} " +
            "ORDER BY ts.id")
    List<TaskStep> findByTaskIdWithServer(@Param("taskId") Long taskId);
    
    /**
     * 根据任务和步骤名称查询
     */
    @Select("SELECT * FROM task_steps WHERE task_id = #{taskId} AND step_name = #{stepName}")
    TaskStep findByTaskAndStepName(@Param("taskId") Long taskId, @Param("stepName") String stepName);
    
    /**
     * 统计任务步骤状态
     */
    @Select("SELECT status, COUNT(*) as count FROM task_steps WHERE task_id = #{taskId} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("taskId") Long taskId);
}
