package com.autotest.mapper;

import com.autotest.entity.PipelineTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PipelineTaskMapper extends BaseMapper<PipelineTask> {
    @Select("SELECT MAX(order_num) FROM pipeline_tasks WHERE pipeline_id = #{pipelineId}")
    Integer selectMaxOrder(Long pipelineId);
}
