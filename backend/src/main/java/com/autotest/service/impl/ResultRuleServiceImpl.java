package com.autotest.service.impl;

import com.autotest.common.PageResult;
import com.autotest.entity.ResultRule;
import com.autotest.mapper.ResultRuleMapper;
import com.autotest.service.ResultRuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结果判定规则服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultRuleServiceImpl implements ResultRuleService {

    private final ResultRuleMapper resultRuleMapper;

    @Override
    public List<ResultRule> listByScriptId(Long scriptId) {
        return resultRuleMapper.selectList(
            new LambdaQueryWrapper<ResultRule>()
                .eq(ResultRule::getScriptId, scriptId)
                .orderByDesc(ResultRule::getCreatedAt)
        );
    }

    @Override
    public List<ResultRule> listGlobalRules() {
        return resultRuleMapper.selectList(
            new LambdaQueryWrapper<ResultRule>()
                .isNull(ResultRule::getScriptId)
                .eq(ResultRule::getEnabled, true)
                .orderByDesc(ResultRule::getCreatedAt)
        );
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        ResultRule rule = resultRuleMapper.selectById(id);
        if (rule != null) {
            rule.setEnabled(enabled);
            rule.setUpdatedAt(LocalDateTime.now());
            resultRuleMapper.updateById(rule);
        }
    }

    @Override
    public PageResult<ResultRule> getPage(Integer page, Integer size, Long scriptId, Boolean enabled) {
        LambdaQueryWrapper<ResultRule> wrapper = new LambdaQueryWrapper<>();
        
        if (scriptId != null) {
            wrapper.eq(ResultRule::getScriptId, scriptId);
        }
        if (enabled != null) {
            wrapper.eq(ResultRule::getEnabled, enabled);
        }
        
        wrapper.orderByDesc(ResultRule::getPriority)
                .orderByDesc(ResultRule::getCreatedAt);

        Page<ResultRule> pageObj = new Page<>(page, size);
        return PageResult.of(resultRuleMapper.selectPage(pageObj, wrapper));
    }

    @Override
    public ResultRule getById(Long id) {
        return resultRuleMapper.selectById(id);
    }

    @Override
    @Transactional
    public ResultRule create(ResultRule rule) {
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        resultRuleMapper.insert(rule);
        return rule;
    }

    @Override
    @Transactional
    public ResultRule update(Long id, ResultRule rule) {
        ResultRule existing = resultRuleMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("规则不存在");
        }

        rule.setId(id);
        rule.setUpdatedAt(LocalDateTime.now());
        resultRuleMapper.updateById(rule);
        return resultRuleMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        resultRuleMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> testRule(Long id, String sampleOutput, Integer exitCode) {
        Map<String, Object> result = new LinkedHashMap<>();

        ResultRule rule = resultRuleMapper.selectById(id);
        if (rule == null) {
            result.put("success", false);
            result.put("error", "规则不存在");
            return result;
        }

        try {
            String judgement = applySingleRule(rule, sampleOutput, exitCode);

            result.put("success", true);
            result.put("result", judgement);
            result.put("ruleName", rule.getName());

            // 返回匹配详情
            Map<String, Object> matchDetails = getMatchDetails(rule, sampleOutput);
            result.put("details", matchDetails);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public String applyRules(Long scriptId, String output, Integer exitCode) {
        // 获取脚本专属规则和全局规则
        LambdaQueryWrapper<ResultRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(ResultRule::getScriptId)
                        .or().eq(ResultRule::getScriptId, scriptId))
                .eq(ResultRule::getEnabled, true)
                .orderByDesc(ResultRule::getPriority);

        List<ResultRule> rules = resultRuleMapper.selectList(wrapper);

        // 按优先级应用规则
        for (ResultRule rule : rules) {
            String result = applySingleRule(rule, output, exitCode);
            if (result != null) {
                return result;
            }
        }

        // 默认规则：退出码判断
        return exitCode == 0 ? "pass" : "fail";
    }

    /**
     * 应用单个规则
     */
    private String applySingleRule(ResultRule rule, String output, Integer exitCode) {
        Map<String, Object> rules = rule.getRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // 检查退出码条件
        List<Integer> exitCodes = (List<Integer>) rules.get("exitCodes");
        if (exitCodes != null && !exitCodes.isEmpty() && exitCode != null) {
            if (!exitCodes.contains(exitCode)) {
                return "fail";
            }
        }

        // 检查成功条件
        Map<String, Object> successCondition = (Map<String, Object>) rules.get("successCondition");
        if (successCondition != null) {
            if (matchCondition(successCondition, output)) {
                return "pass";
            }
        }

        // 检查失败条件
        Map<String, Object> failCondition = (Map<String, Object>) rules.get("failCondition");
        if (failCondition != null) {
            if (matchCondition(failCondition, output)) {
                return "fail";
            }
        }

        // 检查警告条件
        Map<String, Object> warningCondition = (Map<String, Object>) rules.get("warningCondition");
        if (warningCondition != null) {
            if (matchCondition(warningCondition, output)) {
                return "warning";
            }
        }

        return null;
    }

    /**
     * 匹配条件
     */
    private boolean matchCondition(Map<String, Object> condition, String output) {
        String matchType = (String) condition.get("matchType");
        List<String> keywords = (List<String>) condition.get("keywords");
        String pattern = (String) condition.get("pattern");

        if ("any".equals(matchType) && keywords != null) {
            for (String keyword : keywords) {
                if (output.contains(keyword)) {
                    return true;
                }
            }
            return false;
        } else if ("all".equals(matchType) && keywords != null) {
            for (String keyword : keywords) {
                if (!output.contains(keyword)) {
                    return false;
                }
            }
            return true;
        } else if ("regex".equals(matchType) && pattern != null) {
            try {
                Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
                return p.matcher(output).find();
            } catch (Exception e) {
                log.error("正则匹配失败: {}", pattern, e);
                return false;
            }
        }

        return false;
    }

    /**
     * 获取匹配详情
     */
    private Map<String, Object> getMatchDetails(ResultRule rule, String output) {
        Map<String, Object> details = new LinkedHashMap<>();
        Map<String, Object> rules = rule.getRules();

        if (rules == null) {
            return details;
        }

        // 成功条件匹配
        Map<String, Object> successCondition = (Map<String, Object>) rules.get("successCondition");
        if (successCondition != null) {
            List<String> matchedKeywords = findMatchedKeywords(successCondition, output);
            details.put("successKeywords", matchedKeywords);
        }

        // 失败条件匹配
        Map<String, Object> failCondition = (Map<String, Object>) rules.get("failCondition");
        if (failCondition != null) {
            List<String> matchedKeywords = findMatchedKeywords(failCondition, output);
            details.put("failKeywords", matchedKeywords);
        }

        return details;
    }

    /**
     * 查找匹配的关键字
     */
    private List<String> findMatchedKeywords(Map<String, Object> condition, String output) {
        List<String> matched = new ArrayList<>();
        List<String> keywords = (List<String>) condition.get("keywords");

        if (keywords != null) {
            for (String keyword : keywords) {
                if (output.contains(keyword)) {
                    matched.add(keyword);
                }
            }
        }

        String pattern = (String) condition.get("pattern");
        if (pattern != null) {
            try {
                Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
                Matcher m = p.matcher(output);
                while (m.find()) {
                    matched.add(m.group());
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return matched;
    }
}
