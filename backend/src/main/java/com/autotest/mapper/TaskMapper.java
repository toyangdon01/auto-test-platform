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
            "</where>" +
            "ORDER BY t.id DESC" +
            "</script>")
    IPage<Task> selectPageWithScriptName(Page<Task> page, 
                                          @Param("name") String name, 
                                          @Param("status") String status);
}
