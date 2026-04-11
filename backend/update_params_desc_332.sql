-- Update script parameters with descriptions for sol-network-config (script_id=332)
UPDATE script_versions 
SET parameters = '[
  {"name":"TARGET_BMC_IP","type":"string","required":true,"displayName":"目标 BMC IP","description":"目标服务器的 BMC IP 地址，用于建立 SOL 连接"},
  {"name":"TARGET_BMC_USER","type":"string","default":"root","required":true,"displayName":"BMC 用户名","description":"BMC 登录用户名，默认为 root"},
  {"name":"TARGET_BMC_PASS","type":"password","required":true,"displayName":"BMC 密码","description":"BMC 登录密码"},
  {"name":"TARGET_OS_USER","type":"string","default":"root","required":true,"displayName":"操作系统用户名","description":"目标操作系统登录用户名，默认为 root"},
  {"name":"TARGET_OS_PASS","type":"password","required":true,"displayName":"操作系统密码","description":"目标操作系统登录密码"},
  {"name":"CONFIG_TYPE","type":"select","required":true,"default":"single","displayName":"配置类型","description":"网络配置类型：single=单网卡静态 IP，bond=Bond 网卡绑定，dhcp=DHCP 自动获取","options":["single","bond","dhcp"]},
  {"name":"NETWORK_INTERFACE","type":"string","required":false,"displayName":"网卡名称","description":"要配置的网卡接口名称，如 eth0、enp3s0 等 (single/dhcp 模式必需)"},
  {"name":"NETWORK_IP","type":"string","required":false,"displayName":"IP 地址","description":"静态 IP 地址 (single 模式必需)"},
  {"name":"NETWORK_NETMASK","type":"string","default":"255.255.255.0","required":false,"displayName":"子网掩码","description":"子网掩码，如 255.255.255.0"},
  {"name":"NETWORK_GATEWAY","type":"string","required":false,"displayName":"网关","description":"默认网关地址"},
  {"name":"NETWORK_DNS","type":"string","required":false,"displayName":"DNS 服务器","description":"DNS 服务器地址，如 8.8.8.8"},
  {"name":"BOND_NAME","type":"string","default":"bond0","required":false,"displayName":"Bond 接口名","description":"Bond 接口名称，如 bond0"},
  {"name":"BOND_INTERFACES","type":"string","required":false,"displayName":"Bond 从接口","description":"Bond 从接口列表，逗号分隔，如 eth0,eth1"},
  {"name":"BOND_MODE","type":"select","default":"4","required":false,"displayName":"Bond 模式","description":"Bond 工作模式：0=轮询，1=主备，2=XOR，3=广播，4=LACP，5=TLB，6=ALB","options":["0","1","2","3","4","5","6"]},
  {"name":"BOND_MIIMON","type":"string","default":"100","required":false,"displayName":"MII 监控间隔","description":"MII 链路监控间隔，单位毫秒，默认 100ms"},
  {"name":"BOND_PRIMARY","type":"string","required":false,"displayName":"主接口","description":"主备模式 (mode=1) 下的主接口名称"},
  {"name":"BOND_HASH_POLICY","type":"select","default":"layer3+4","required":false,"displayName":"Hash 策略","description":"LACP 模式 (mode=4) 的 Hash 策略：layer2、layer2+3、layer3+4 等","options":["layer2","layer2+3","layer3+4","encap2+3","encap3+4"]}
]'::jsonb 
WHERE script_id = 332;
