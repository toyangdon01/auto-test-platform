package com.autotest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.autotest.entity.ScriptResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 脚本资源关联 Mapper
 */
@Mapper
public interface ScriptResourceMapper extends BaseMapper<ScriptResource> {

    /**
     * 获取脚本的资源列表（包含资源文件信息）
     */
    @Select("SELECT sr.*, rf.name, rf.storage_path, rf.file_size, rf.file_type, rf.category, rf.checksum " +
            "FROM script_resources sr " +
            "LEFT JOIN resource_files rf ON sr.resource_id = rf.id " +
            "WHERE sr.script_id = #{scriptId} " +
            "ORDER BY sr.upload_order ASC")
    List<ScriptResource> findByScriptIdWithResource(Long scriptId);
}
