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
     * 分页查询结果（冗余字段已存储名称，无需 JOIN）
     */
    @Select("<script>" +
            "SELECT * FROM test_results " +
            "<where>" +
            "  <if test='taskId != null'>" +
            "    AND task_id = #{taskId}" +
            "  </if>" +
            "  <if test='serverId != null'>" +
            "    AND server_id = #{serverId}" +
            "  </if>" +
            "  <if test='result != null and result != \"\"'>" +
            "    AND result = #{result}" +
            "  </if>" +
            "  <if test='scriptId != null'>" +
            "    AND script_id = #{scriptId}" +
            "  </if>" +
            "  <if test='keyword != null and keyword != \"\"'>" +
            "    AND (task_name LIKE CONCAT('%', #{keyword}, '%') OR server_name LIKE CONCAT('%', #{keyword}, '%'))" +
            "  </if>" +
            "</where>" +
            "ORDER BY id DESC" +
            "</script>")
    IPage<TestResult> selectPageWithNames(Page<TestResult> page,
                                           @Param("taskId") Long taskId,
                                           @Param("serverId") Long serverId,
                                           @Param("result") String result,
                                           @Param("scriptId") Long scriptId,
                                           @Param("keyword") String keyword);
}