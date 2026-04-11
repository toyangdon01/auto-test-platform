-- Update script parameters for sol-network-config (script_id=322)
UPDATE script_versions 
SET parameters = '[
  {"name":"TARGET_BMC_IP","type":"string","required":true,"displayName":"BMC IP"},
  {"name":"TARGET_BMC_USER","type":"string","default":"root","required":true,"displayName":"BMC User"},
  {"name":"TARGET_BMC_PASS","type":"string","required":true,"displayName":"BMC Pass"},
  {"name":"TARGET_OS_USER","type":"string","default":"root","required":true,"displayName":"OS User"},
  {"name":"TARGET_OS_PASS","type":"string","required":true,"displayName":"OS Pass"},
  {"name":"CONFIG_TYPE","type":"string","required":true,"default":"single","displayName":"Config Type"},
  {"name":"NETWORK_INTERFACE","type":"string","required":false,"displayName":"Interface"},
  {"name":"NETWORK_IP","type":"string","required":false,"displayName":"IP"},
  {"name":"NETWORK_NETMASK","type":"string","default":"255.255.255.0","required":false,"displayName":"Netmask"},
  {"name":"NETWORK_GATEWAY","type":"string","required":false,"displayName":"Gateway"},
  {"name":"NETWORK_DNS","type":"string","required":false,"displayName":"DNS"},
  {"name":"BOND_NAME","type":"string","default":"bond0","required":false,"displayName":"Bond Name"},
  {"name":"BOND_INTERFACES","type":"string","required":false,"displayName":"Bond Ifaces"},
  {"name":"BOND_MODE","type":"string","default":"4","required":false,"displayName":"Bond Mode"},
  {"name":"BOND_MIIMON","type":"string","default":"100","required":false,"displayName":"MII Mon"},
  {"name":"BOND_PRIMARY","type":"string","required":false,"displayName":"Primary"},
  {"name":"BOND_HASH_POLICY","type":"string","default":"layer3+4","required":false,"displayName":"Hash Policy"}
]'::jsonb 
WHERE script_id = 322;
