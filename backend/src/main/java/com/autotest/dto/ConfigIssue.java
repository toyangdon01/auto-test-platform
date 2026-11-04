package com.autotest.dto;

/**
 * autotest.yaml 的一条校验问题。
 * path 指向出问题的位置（如 steps.step_1.script），hint 给可执行的改法，
 * 让智能体不必读文档就能自己改对。
 *
 * @author auto-test-platform
 */
public record ConfigIssue(String code, String path, String message, String hint) {

    public String toText() {
        return message + (hint == null || hint.isBlank() ? "" : "；" + hint);
    }
}
