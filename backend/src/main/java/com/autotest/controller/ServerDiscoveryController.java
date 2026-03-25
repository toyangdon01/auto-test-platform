package com.autotest.controller;

import com.autotest.entity.Server;
import com.autotest.mapper.ServerMapper;
import com.autotest.service.SshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 服务器资源发现控制器
 */
@Slf4j
@RestController
@RequestMapping("/servers/discovery")
@RequiredArgsConstructor
public class ServerDiscoveryController {

    private final ServerMapper serverMapper;

    /**
     * 获取服务器的磁盘设备列表
     */
    @GetMapping("/{serverId}/disks")
    public Map<String, Object> getDiskDevices(@PathVariable Long serverId) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            Server server = serverMapper.selectById(serverId);
            if (server == null) {
                result.put("code", 404);
                result.put("message", "服务器不存在");
                return result;
            }

            // 执行命令获取磁盘列表
            String cmd = "lsblk -dn -o NAME,TYPE,SIZE | grep -E 'disk|lvm' | awk '{print $1}'";
            SshService.ExecuteResult execResult = SshService.executeCommand(server, cmd, null, 5000);
            
            List<Map<String, String>> devices = new ArrayList<>();
            if (execResult.getExitCode() == 0) {
                String[] lines = execResult.getOutput().split("\n");
                for (String line : lines) {
                    String device = line.trim();
                    if (!device.isEmpty()) {
                        Map<String, String> deviceInfo = new LinkedHashMap<>();
                        deviceInfo.put("name", device);
                        deviceInfo.put("label", device);
                        devices.add(deviceInfo);
                    }
                }
            }

            result.put("code", 0);
            result.put("message", "success");
            result.put("data", devices);

        } catch (Exception e) {
            log.error("获取磁盘设备失败", e);
            result.put("code", 500);
            result.put("message", "获取磁盘设备失败：" + e.getMessage());
            result.put("data", new ArrayList<>());
        }

        return result;
    }

    /**
     * 获取服务器的网卡接口列表
     */
    @GetMapping("/{serverId}/interfaces")
    public Map<String, Object> getNetworkInterfaces(@PathVariable Long serverId) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            Server server = serverMapper.selectById(serverId);
            if (server == null) {
                result.put("code", 404);
                result.put("message", "服务器不存在");
                return result;
            }

            // 执行命令获取网卡列表（排除 lo 回环接口）
            String cmd = "ls /sys/class/net | grep -v '^lo$'";
            SshService.ExecuteResult execResult = SshService.executeCommand(server, cmd, null, 5000);
            
            List<Map<String, String>> interfaces = new ArrayList<>();
            if (execResult.getExitCode() == 0) {
                String[] lines = execResult.getOutput().split("\n");
                for (String line : lines) {
                    String iface = line.trim();
                    if (!iface.isEmpty()) {
                        Map<String, String> ifaceInfo = new LinkedHashMap<>();
                        ifaceInfo.put("name", iface);
                        ifaceInfo.put("label", iface);
                        interfaces.add(ifaceInfo);
                    }
                }
            }

            result.put("code", 0);
            result.put("message", "success");
            result.put("data", interfaces);

        } catch (Exception e) {
            log.error("获取网卡接口失败", e);
            result.put("code", 500);
            result.put("message", "获取网卡接口失败：" + e.getMessage());
            result.put("data", new ArrayList<>());
        }

        return result;
    }

    /**
     * 获取多个服务器的设备和网卡汇总（用于多服务器任务）
     */
    @PostMapping("/resources")
    public Map<String, Object> getMultiServerResources(@RequestBody Map<String, List<Long>> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            List<Long> serverIds = params.get("serverIds");
            if (serverIds == null || serverIds.isEmpty()) {
                result.put("code", 400);
                result.put("message", "服务器 ID 列表不能为空");
                return result;
            }

            Set<String> allDisks = new TreeSet<>();
            Set<String> allInterfaces = new TreeSet<>();

            for (Long serverId : serverIds) {
                Server server = serverMapper.selectById(serverId);
                if (server == null) continue;

                // 获取磁盘
                try {
                    String diskCmd = "lsblk -dn -o NAME,TYPE,SIZE | grep -E 'disk|lvm' | awk '{print $1}'";
                    SshService.ExecuteResult diskResult = SshService.executeCommand(server, diskCmd, null, 5000);
                    if (diskResult.getExitCode() == 0) {
                        Arrays.stream(diskResult.getOutput().split("\n"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(allDisks::add);
                    }
                } catch (Exception e) {
                    log.warn("获取服务器 {} 磁盘失败：{}", serverId, e.getMessage());
                }

                // 获取网卡
                try {
                    String ifaceCmd = "ls /sys/class/net | grep -v '^lo$'";
                    SshService.ExecuteResult ifaceResult = SshService.executeCommand(server, ifaceCmd, null, 5000);
                    if (ifaceResult.getExitCode() == 0) {
                        Arrays.stream(ifaceResult.getOutput().split("\n"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(allInterfaces::add);
                    }
                } catch (Exception e) {
                    log.warn("获取服务器 {} 网卡失败：{}", serverId, e.getMessage());
                }
            }

            List<Map<String, String>> disks = new ArrayList<>();
            for (String disk : allDisks) {
                Map<String, String> diskInfo = new LinkedHashMap<>();
                diskInfo.put("name", disk);
                diskInfo.put("label", disk);
                disks.add(diskInfo);
            }

            List<Map<String, String>> interfaces = new ArrayList<>();
            for (String iface : allInterfaces) {
                Map<String, String> ifaceInfo = new LinkedHashMap<>();
                ifaceInfo.put("name", iface);
                ifaceInfo.put("label", iface);
                interfaces.add(ifaceInfo);
            }

            result.put("code", 0);
            result.put("message", "success");
            result.put("data", Map.of(
                "disks", disks,
                "interfaces", interfaces
            ));

        } catch (Exception e) {
            log.error("获取多服务器资源失败", e);
            result.put("code", 500);
            result.put("message", "获取资源失败：" + e.getMessage());
            result.put("data", Map.of("disks", new ArrayList<>(), "interfaces", new ArrayList<>()));
        }

        return result;
    }
}
