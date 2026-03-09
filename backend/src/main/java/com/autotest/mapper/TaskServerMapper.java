package com.autotest.mapper;

import com.autotest.entity.TaskServer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务服务器关联 Mapper
 *
 * @author auto-test-platform
 */
@Mapper
public interface TaskServerMapper extends BaseMapper<TaskServer> {
}
