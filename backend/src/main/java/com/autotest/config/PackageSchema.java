package com.autotest.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * autotest.yaml 的字段契约，校验器与 describe_package_spec 工具共用同一份定义，避免两处文字漂移。
 * 这里的键集合必须与 ScriptConfigService 的手工解析代码保持一致：解析是逐键读取的，
 * 没列在这里的键既不报错也不生效（保存时还会连注释一起被抹掉），所以它同时是"合法"的定义。
 */
public final class PackageSchema {

    /** 顶层键 */
    public static final Set<String> TOP_LEVEL = ordered("name", "description", "type", "category",
            "timeout", "parameters", "steps", "resources", "tags");

    /** parameters[] 每一项 */
    public static final Set<String> PARAMETER = ordered("name", "type", "default", "defaultValue",
            "description", "displayName", "required", "options");

    /** steps.<步骤名> 每一项；_meta 是保留键，只放元数据、不参与执行 */
    public static final Set<String> STEP = ordered("displayName", "script", "dependsOn", "resultParser",
            "resultCollector", "params", "startupProbe", "parseRule", "resources", "fileCollectEnabled",
            "fileCollects");

    /** resources[] 每一项 */
    public static final Set<String> RESOURCE = ordered("resourceId", "resourceMd5", "targetPath",
            "permissions", "order");

    /** tags[] 每一项 */
    public static final Set<String> TAG = ordered("key", "value");

    /** parameters[].options[] 每一项 */
    public static final Set<String> OPTION = ordered("label", "value");

    public static final String META_STEP = "_meta";

    /** 会被解析成可执行文件的后缀；用于判断 script 指向的是不是包内文件 */
    public static final Set<String> SCRIPT_SUFFIXES = ordered(".sh", ".bash", ".py", ".exp", ".ps1", ".cmd", ".jar");

    /** 在 Linux 上解释执行、必须是 LF 换行的文件后缀 */
    public static final Set<String> LF_ONLY_SUFFIXES = ordered(".sh", ".bash", ".py", ".exp");

    /** 顶层 type 的合法值，其它值会被当成 shell 处理 */
    public static final Set<String> SCRIPT_TYPES = ordered("shell", "powershell");

    /** 硬规则：违反后平台不报错但行为不符合预期 */
    public static final List<String> RULES = List.of(
            "steps 必须是「步骤名 -> 步骤配置」的映射；写成数组也能导入（平台按 name 或 step_N 转成映射），但推荐写映射。",
            "步骤里没有超时字段：步骤超时来自任务的 timeout（单位毫秒），任务没填时回落到 scripts.default_timeout（单位秒），也就是顶层 timeout。",
            "顶层 timeout 在创建脚本时写进 scripts.default_timeout；界面上留空才按包内声明，填了就以界面值为准。",
            "共享参数 parameters[].default 只做界面预填，执行时不会注入环境变量；必填项必须在提交任务时显式传值。",
            "步骤参数 steps.<步骤名>.params 的 defaultValue（也接受 default）会注入环境变量，并且覆盖同名共享参数的取值。",
            "options 是取值白名单：界面会渲染成下拉框，提交任务按 value 严格匹配（trim 后区分大小写）。一旦声明，白名单之外的值全部被拒。",
            "required 必须是 YAML 布尔值（true/false），写成 \"true\" 字符串会被忽略。",
            "script 优先按包内相对路径解析（支持 steps/step1.sh 这样的子目录）；既不是包内文件也不像命令时会被当成 shell 命令执行，报 exit 127。",
            "脚本文件必须是 LF 换行。Windows 上写出的 CRLF 在 Linux 上会以 $'\\r': command not found 失败，平台不做转换。",
            "覆盖导入（strategy=OVERWRITE）用包里的声明整体替换参数定义，界面上手填的参数不保留；它只重写当前版本记录，不新增版本。",
            "resources 用 resourceMd5 或 resourceId 关联平台资源库里的二进制；内联创建（create_script）带不进二进制资源，只能先把包落到平台主机目录再 import_package。",
            "一个脚本只允许一个步骤开结果收集：resultParser/resultCollector 为 true 的步骤多于一个时，每台服务器会插入多行 test_results，而这张表没有步骤归属列，事后分不清哪行来自哪步（严格校验按 RESULT_COLLECTOR_MULTIPLE 拒）。",
            "startupProbe、parseRule、fileCollects 三件都只在 SSH 远程步骤执行；在平台本机跑（serverId=-1）时整段跳过 —— 步骤退出码为 0 就直接落库，不探测、不解析、不收文件。",
            "probe 的内层键不校验：type=tcp 却没写 port、或命令模式没写 command 时，探测按「通过」处理，既不失败也不真的探测。",
            "解析失败不改变步骤状态：步骤仍是 success，原因只写进 test_results.result_reason 和步骤日志的 [WARN] 行；解析产物同时落 test_results.parsed_data 与 task_steps.parsed_result。"
    );

    /** 顶层键的用途说明，供 describe_package_spec 返回 */
    public static Map<String, String> topLevelDocs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("name", "包名。导入时脚本名取包目录名，这里不一致只影响重导入");
        docs.put("description", "脚本描述，落到 scripts.description");
        docs.put("type", "shell 或 powershell，缺省按 shell");
        docs.put("category", "测试分类，如 compute/network/storage/database");
        docs.put("timeout", "整数秒。创建时写进 scripts.default_timeout，任务没配超时时用它");
        docs.put("parameters", "共享参数定义数组，形状见 parameterKeys");
        docs.put("steps", "步骤映射：步骤名 -> 步骤配置，顺序无关，执行顺序由 dependsOn 决定");
        docs.put("resources", "包级资源关联数组，靠 resourceMd5/resourceId 指向平台资源库");
        docs.put("tags", "标签数组 [{key,value}]，落到 scripts.tags");
        return docs;
    }

    public static Map<String, String> parameterDocs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("name", "参数名，脚本里用 $NAME 取；同一作用域内必须唯一");
        docs.put("type", "string/number/boolean/select 等，只影响界面渲染");
        docs.put("default", "共享参数默认值：只预填界面，不注入环境变量");
        docs.put("defaultValue", "步骤参数默认值：会注入环境变量（共享参数里等价于 default）");
        docs.put("description", "说明");
        docs.put("displayName", "界面展示名，不影响取值");
        docs.put("required", "布尔值。true 时提交任务缺值直接被拒");
        docs.put("options", "取值白名单 [{label,value}]，也接受裸字符串；声明后白名单外的取值全被拒");
        return docs;
    }

    public static Map<String, String> stepDocs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("displayName", "步骤展示名");
        docs.put("script", "包内相对路径（支持子目录）或一段 shell 命令");
        docs.put("dependsOn", "前置步骤名数组，成环或指向不存在的步骤会导致任务不执行");
        docs.put("resultParser", "布尔值，是否解析结果指标");
        docs.put("resultCollector", "布尔值，是否收集结果；缺省时跟随 resultParser");
        docs.put("params", "步骤参数：参数定义数组（形状同共享参数）或直接的键值对");
        docs.put("startupProbe", "启动探测配置，键见 probeKeys；只在 SSH 远程步骤生效");
        docs.put("parseRule", "结果解析规则，键见 parseRuleKeys；只在 resultParser/resultCollector 为 true 时执行");
        docs.put("resources", "步骤级资源，形状同 resources[]（见 resourceKeys）");
        docs.put("fileCollectEnabled", "布尔值，是否收集文件；false 时 fileCollects 整段不跑");
        docs.put("fileCollects", "文件收集配置数组，键见 fileCollectKeys；只在 SSH 远程步骤生效");
        return docs;
    }

    /** steps.<步骤名>.startupProbe 的每一项；内层键过去无人校验，也不做校验，只把真实形状讲清楚 */
    public static final Set<String> PROBE = ordered("type", "port", "url", "command", "timeoutSeconds");

    public static Map<String, String> probeDocs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("type", "tcp（缺省）/ http / 其它值走命令模式");
        docs.put("port", "tcp 与 http 模式探测的端口。注意探测固定在目标机的 127.0.0.1 上，"
                + "没有 host 键可改 —— 语义是「这台机器上的服务起来没」");
        docs.put("url", "http 模式的路径或完整 URL；给了 port 且不以 http 开头时拼成 http://127.0.0.1:<port><url>");
        docs.put("command", "非 tcp/http 模式下在目标机执行的命令，退出码 0 视为就绪");
        docs.put("timeoutSeconds", "轮询总时长（秒），默认 60；超时后步骤判失败，错误信息是「启动探测失败」");
        return docs;
    }

    /** steps.<步骤名>.parseRule 的每一项 */
    public static final Set<String> PARSE_RULE = ordered("parserType", "builtinFormat", "inputSource",
            "filePattern", "scriptSource", "scriptContent", "scriptLanguage");

    public static Map<String, String> parseRuleDocs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("parserType", "builtin 走内置解析；其它取值走脚本解析");
        docs.put("builtinFormat", "只认 key_value（逐行 k=v 或 k: v）和 json；写成别的值报「未知的内置格式」");
        docs.put("inputSource", "stdout 用步骤输出；file 用 filePattern 指向的文件（在目标机 cat 读取，读不到只记日志）");
        docs.put("filePattern", "inputSource=file 时必填，支持内置参数占位（与脚本参数同名可替换）");
        docs.put("scriptSource", "脚本解析模式：inline 用 scriptContent / package 从脚本包读");
        docs.put("scriptContent", "inline 模式的解析脚本，其标准输出必须是 JSON");
        docs.put("scriptLanguage", "解析脚本语言，缺省按 python 处理");
        return docs;
    }

    /** steps.<步骤名>.fileCollects[] 的每一项 */
    public static final Set<String> FILE_COLLECT = ordered("name", "path", "type");

    public static Map<String, String> fileCollectDocs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("name", "产物名，落到 task_steps.output_files，最终在 get_task_result 的 artifacts[] 里可见");
        docs.put("path", "目标机上的文件或目录路径");
        docs.put("type", "file / directory / pattern");
        return docs;
    }

    /** 严格校验（默认开启）会拒绝的问题，key 是 reason code */
    public static Map<String, String> validationCodes() {
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put("ROOT_NOT_OBJECT", "autotest.yaml 顶层不是映射");
        codes.put("UNKNOWN_KEY", "任何一层出现没定义的键，会被静默丢弃");
        codes.put("TYPE_INVALID", "type 不是 shell/powershell");
        codes.put("TIMEOUT_INVALID", "timeout 不是正整数");
        codes.put("PARAMETERS_NOT_ARRAY", "parameters 不是数组");
        codes.put("PARAMETER_NOT_OBJECT", "某个参数定义不是映射");
        codes.put("PARAMETER_NAME_MISSING", "参数没有 name");
        codes.put("PARAMETER_NAME_DUPLICATED", "同一作用域里参数名重复");
        codes.put("PARAMETER_REQUIRED_NOT_BOOLEAN", "required 写成了字符串");
        codes.put("OPTIONS_NOT_ARRAY", "options 不是数组");
        codes.put("OPTION_VALUE_EMPTY", "options 里某项取不到提交值，等于拒绝所有取值");
        codes.put("STEPS_MISSING", "没有 steps，任务不会执行任何步骤");
        codes.put("STEPS_NOT_MAP", "steps 既不是映射也不是数组");
        codes.put("STEPS_EMPTY", "steps 里只有 _meta");
        codes.put("STEP_NOT_OBJECT", "某个步骤的配置不是映射");
        codes.put("STEP_SCRIPT_NOT_FOUND", "script 既不是包内文件也不像命令，执行时 exit 127");
        codes.put("RESULT_COLLECTOR_MULTIPLE", "两个及以上步骤开了结果收集，一台机器出多行结果且无法区分来自哪一步");
        codes.put("DEPENDS_ON_NOT_ARRAY", "dependsOn 不是数组");
        codes.put("DEPENDS_ON_SELF", "步骤依赖自己");
        codes.put("DEPENDS_ON_UNKNOWN", "dependsOn 指向不存在的步骤，任务一直等");
        codes.put("DEPENDENCY_CYCLE", "步骤依赖成环，任务直接终止");
        codes.put("RESOURCES_NOT_ARRAY", "resources 不是数组");
        codes.put("RESOURCE_NOT_OBJECT", "某个资源项不是映射");
        codes.put("RESOURCE_LOCATOR_MISSING", "资源项既没有 resourceMd5 也没有 resourceId");
        codes.put("TAGS_NOT_ARRAY", "tags 不是数组，标签整段丢失");
        codes.put("TAG_NOT_OBJECT", "某个标签不是 {key,value} 映射");
        codes.put("CRLF_SCRIPT", "包内脚本文件是 CRLF 换行");
        return codes;
    }

    /** 一个最小但能跑的包 */
    public static String exampleYaml() {
        return """
                name: demo-package
                description: 最小可跑示例：跑一个脚本、产出一个指标
                type: shell
                category: compute
                timeout: 600
                tags:
                  - key: os
                    value: kylin
                parameters:
                  - name: THREADS
                    displayName: 并发线程数
                    type: number
                    default: 4
                    required: true
                    description: 只预填界面，执行时必须显式传值
                  - name: MODE
                    type: select
                    options:
                      - label: 快速
                        value: fast
                      - label: 完整
                        value: full
                steps:
                  step_1:
                    displayName: 执行
                    script: main.sh
                    dependsOn: []
                    resultParser: true
                    params:
                      - name: ROUNDS
                        type: number
                        defaultValue: 3
                        description: 会注入 ROUNDS=3，并覆盖同名共享参数
                """;
    }

    /** 常见错法 -> 症状与改法 */
    public static List<Map<String, String>> commonMistakes() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(mistake("写 steps 数组", "导入不报错，但步骤名变成 step_1、step_2",
                "改成「步骤名 -> 配置」的映射"));
        list.add(mistake("顶层未知键（如 version、entrypoint）", "被静默丢弃，下次保存脚本连注释一起消失",
                "只用 topLevelKeys 里列出的键"));
        list.add(mistake("script: main.sch 拼错", "导入成功，执行时当成 shell 命令 -> exit 127",
                "严格校验会直接拒；确认包内相对路径存在"));
        list.add(mistake("给步骤配 timeout", "不生效",
                "超时是任务级：run_test 传 timeout（毫秒），或改顶层 timeout（秒）"));
        list.add(mistake("指望共享参数的 default 注入环境变量", "脚本里取到空值",
                "必填项在提交任务时显式传值，或把默认值写进步骤 params"));
        list.add(mistake("options 里只填 label 不填 value", "白名单声明了却没有任何合法值，所有取值被拒",
                "label/value 都填；只填一个时另一个会跟着补，两个都空才算无效"));
        list.add(mistake("Windows 上写的 CRLF 脚本", "$'\\r': command not found",
                "保存为 LF；严格校验会在导入时就拒掉"));
        list.add(mistake("UI 上传带 <包名>/ 目录的 zip", "配置回填不了、name 也对不上",
                "把 autotest.yaml 放到压缩包根目录；import_package 走目录时没这个限制"));
        list.add(mistake("两个步骤都写 resultParser: true", "每台服务器插入多行 test_results，表里没有步骤归属列，分不清哪行来自哪步",
                "只让产出测试结果的那一步开收集；严格校验按 RESULT_COLLECTOR_MULTIPLE 直接拒"));
        list.add(mistake("在平台本机（serverIds:[-1]）跑还配 startupProbe/parseRule/fileCollects",
                "三个都不执行，步骤退出码 0 就算完，既不探测也不产解析结果，而且没有任何提示",
                "要这三件就选远程主机；否则把「等就绪」写进步骤自己的脚本里"));
        list.add(mistake("parseRule.builtinFormat 写成 metrics / csv / text",
                "解析阶段抛「未知的内置格式」，步骤仍是 success，只有日志里一行 [WARN]",
                "只有 key_value 和 json 两种；要更复杂的解析就走脚本解析（scriptSource: inline）"));
        return list;
    }

    /** 前缀命中或编辑距离不超过 2 时给出候选，帮智能体一眼看出是拼错 */
    public static String nearest(String key, Set<String> allowed) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String lowered = key.trim().toLowerCase();
        for (String candidate : allowed) {
            if (candidate.toLowerCase().startsWith(lowered)) {
                return candidate;
            }
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : allowed) {
            int distance = levenshtein(lowered, candidate.toLowerCase());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= 2 ? best : null;
    }

    private static Map<String, String> mistake(String wrong, String symptom, String fix) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("wrong", wrong);
        row.put("symptom", symptom);
        row.put("fix", fix);
        return row;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static Set<String> ordered(String... keys) {
        return new LinkedHashSet<>(Arrays.asList(keys));
    }

    private PackageSchema() {
    }
}
