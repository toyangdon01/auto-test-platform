package com.autotest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.autotest.entity.ResourceFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 资源文件 Mapper
 */
@Mapper
public interface ResourceFileMapper extends BaseMapper<ResourceFile> {

    /**
     * 根据MD5查找文件
     */
    @Select("SELECT * FROM resource_files WHERE checksum = #{checksum}")
    ResourceFile findByChecksum(String checksum);
}
