package com.autotest.mapper;

import com.autotest.entity.TestResult;
import com.autotest.handler.JsonbTypeHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 测试结果 Mapper
 *
 * @author auto-test-platform
 */
@Mapper
public interface TestResultMapper extends BaseMapper<TestResult> {
    
    /**
     * 分页查询结果（通过 LEFT JOIN tasks 关联查询 script_id）
     */
    @Select("<script>" +
            "SELECT tr.* FROM test_results tr " +
            "LEFT JOIN tasks t ON tr.task_id = t.id " +
            "<where>" +
            "  <if test='taskId != null'>" +
            "    AND tr.task_id = #{taskId}" +
            "  </if>" +
            "  <if test='serverId != null'>" +
            "    AND tr.server_id = #{serverId}" +
            "  </if>" +
            "  <if test='result != null and result != &quot;&quot;'>" +
            "    AND tr.result = #{result}" +
            "  </if>" +
            "  <if test='scriptId != null'>" +
            "    AND t.script_id = #{scriptId}" +
            "  </if>" +
            "  <if test='keyword != null and keyword != &quot;&quot;'>" +
            "    AND (tr.task_name LIKE CONCAT('%', #{keyword}, '%') OR tr.server_name LIKE CONCAT('%', #{keyword}, '%'))" +
            "  </if>" +
            "</where>" +
            "ORDER BY tr.created_at DESC" +
            "</script>")
    IPage<TestResult> selectPageWithNames(Page<TestResult> page,
                                           @Param("taskId") Long taskId,
                                           @Param("serverId") Long serverId,
                                           @Param("result") String result,
                                           @Param("scriptId") Long scriptId,
                                           @Param("keyword") String keyword);
}