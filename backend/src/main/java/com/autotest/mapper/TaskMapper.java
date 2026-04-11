package com.autotest.mapper;

import com.autotest.entity.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务 Mapper
 *
 * @author auto-test-platform
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
    
    /**
     * 分页查询任务（带脚本名称）
     */
    @Select("<script>" +
            "SELECT t.*, s.name as script_name " +
            "FROM tasks t " +
            "LEFT JOIN scripts s ON t.script_id = s.id " +
            "<where>" +
            "  <if test='name != null and name != \"\"'>" +
            "    AND t.name LIKE CONCAT('%', #{name}, '%')" +
            "  </if>" +
            "  <if test='status != null and status != \"\"'>" +
            "    AND t.status = #{status}" +
            "  </if>" +
            "  <if test='scriptId != null'>" +
            "    AND t.script_id = #{scriptId}" +
            "  </if>" +
            "</where>" +
            "ORDER BY t.id DESC" +
            "</script>")
    IPage<Task> selectPageWithScriptName(Page<Task> page, 
                                          @Param("name") String name, 
                                          @Param("status") String status,
                                          @Param("scriptId") Long scriptId);
    
    /**
     * 查询任务详情（确保 JSONB 字段被正确加载）
     */
    @Select("SELECT id, name, description, script_id, script_version, " +
            "       shared_params, step_params, step_server_mapping, role_execution_strategy, " +
            "       status, deploy_status, run_status, cleanup_status, " +
            "       total_servers, completed_servers, passed_servers, failed_servers, " +
            "       started_at, finished_at, created_at, updated_at " +
            "FROM tasks WHERE id = #{taskId}")
    Task selectTaskWithParams(@Param("taskId") Long taskId);
}
