#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证端口配置"""

import sys
import time
import re
import socket
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

import paramiko


def recv_all(channel, timeout=3):
    output = ''
    channel.settimeout(timeout)
    start = time.time()
    while time.time() - start < timeout:
        try:
            data = channel.recv(4096)
            if data:
                output += data.decode('utf-8', errors='ignore')
                time.sleep(0.2)
            else:
                break
        except socket.timeout:
            break
        except:
            break
    return output


client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.15.254', 22, 'huawei123', 'Huawei@123', timeout=30)

channel = client.invoke_shell()
time.sleep(2)
recv_all(channel, 2)

channel.send(b'N\n')
time.sleep(2)
recv_all(channel, 3)

channel.send(b'sys\n')
time.sleep(2)
recv_all(channel, 3)

# 检查端口 31-36 的速度
print("\n===== Port Configuration Status =====")
for port in range(31, 37):
    port_name = f'25ge 1/0/{port}'
    channel.send(f'display interface {port_name}\n'.encode())
    time.sleep(2)
    output = recv_all(channel, 3)
    
    speed_match = re.search(r'Speed:\s+(\d+)', output)
    state_match = re.search(r'current state\s*:\s*(\w+)', output)
    
    speed = speed_match.group(1) if speed_match else 'N/A'
    state = state_match.group(1) if state_match else 'N/A'
    
    print(f'{port_name}: Speed={speed}, State={state}')

channel.send(b'quit\n')
client.close()
print("\nDone!")