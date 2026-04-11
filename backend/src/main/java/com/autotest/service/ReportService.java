package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.dto.ReportGenerateRequest;
import com.autotest.dto.ReportDetailResponse;
import com.autotest.entity.Report;

import java.util.List;

/**
 * 报告服务接口
 *
 * @author auto-test-platform
 */
public interface ReportService {

    /**
     * 分页查询报告列表
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @param taskId   任务ID（可选）
     * @return 分页结果
     */
    PageResult<Report> getList(int page, int pageSize, Long taskId);

    /**
     * 获取报告详情
     *
     * @param id 报告ID
     * @return 报告详情
     */
    ReportDetailResponse getDetail(Long id);

    /**
     * 生成报告
     *
     * @param request 生成请求
     * @return 生成的报告
     */
    Report generate(ReportGenerateRequest request);

    /**
     * 删除报告
     *
     * @param id 报告ID
     */
    void delete(Long id);

    /**
     * 获取报告文件路径
     *
     * @param id 报告ID
     * @return 文件路径
     */
    String getFilePath(Long id);
}
