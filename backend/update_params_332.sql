-- Update script parameters for sol-network-config (script_id=332)
UPDATE script_versions 
SET parameters = '[
  {"name":"TARGET_BMC_IP","type":"string","required":true,"displayName":"目标 BMC IP"},
  {"name":"TARGET_BMC_USER","type":"string","default":"root","required":true,"displayName":"BMC 用户名"},
  {"name":"TARGET_BMC_PASS","type":"password","required":true,"displayName":"BMC 密码"},
  {"name":"TARGET_OS_USER","type":"string","default":"root","required":true,"displayName":"操作系统用户名"},
  {"name":"TARGET_OS_PASS","type":"password","required":true,"displayName":"操作系统密码"},
  {"name":"CONFIG_TYPE","type":"select","required":true,"default":"single","displayName":"配置类型","options":["single","bond","dhcp"]},
  {"name":"NETWORK_INTERFACE","type":"string","required":false,"displayName":"网卡名称"},
  {"name":"NETWORK_IP","type":"string","required":false,"displayName":"IP 地址"},
  {"name":"NETWORK_NETMASK","type":"string","default":"255.255.255.0","required":false,"displayName":"子网掩码"},
  {"name":"NETWORK_GATEWAY","type":"string","required":false,"displayName":"网关"},
  {"name":"NETWORK_DNS","type":"string","required":false,"displayName":"DNS 服务器"},
  {"name":"BOND_NAME","type":"string","default":"bond0","required":false,"displayName":"Bond 接口名"},
  {"name":"BOND_INTERFACES","type":"string","required":false,"displayName":"Bond 从接口"},
  {"name":"BOND_MODE","type":"select","default":"4","required":false,"displayName":"Bond 模式","options":["0","1","2","3","4","5","6"]},
  {"name":"BOND_MIIMON","type":"string","default":"100","required":false,"displayName":"MII 监控间隔"},
  {"name":"BOND_PRIMARY","type":"string","required":false,"displayName":"主接口"},
  {"name":"BOND_HASH_POLICY","type":"select","default":"layer3+4","required":false,"displayName":"Hash 策略","options":["layer2","layer2+3","layer3+4","encap2+3","encap3+4"]}
]'::jsonb 
WHERE script_id = 332;
