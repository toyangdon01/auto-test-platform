#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Huawei Switch Port Configuration Script
将 25GE 端口配置为 10GE
"""

import sys
import os
import time
import io
import socket
import re

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

import paramiko


def recv_all(channel, timeout=3):
    output = ""
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
        except Exception:
            break
    return output


def configure_huawei_switch(switch_ip, username, password, port_range):
    """配置华为交换机端口"""
    
    print("=" * 50)
    print("Huawei Switch Port Configuration")
    print("=" * 50)
    print(f"Switch IP: {switch_ip}")
    print(f"Port Range: {port_range}")
    print("=" * 50)
    
    # 连接 SSH
    print("\n[1/7] Connecting to switch...")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    
    try:
        client.connect(switch_ip, 22, username, password, timeout=30)
    except Exception as e:
        print(f"ERROR: SSH connection failed: {e}")
        sys.exit(1)
    print("Connected")
    
    channel = client.invoke_shell()
    time.sleep(2)
    recv_all(channel, 2)
    
    # 跳过密码修改提示
    print("\n[2/7] Handling password prompt...")
    channel.send(b"\n")
    time.sleep(2)
    output = recv_all(channel, 3)
    
    if "change now" in output.lower():
        print("Skipping password change...")
        channel.send(b"N\n")
        time.sleep(2)
        recv_all(channel, 3)
    
    # 进入系统视图
    print("\n[3/7] Entering system view...")
    channel.send(b"sys\n")
    time.sleep(2)
    recv_all(channel, 3)
    print("Entered system view")
    
    # 关闭分页（避免 'More' 提示）
    print("\n[4/7] Disabling pagination...")
    channel.send(b"screen-length 0 temporary\n")
    time.sleep(1)
    recv_all(channel, 2)
    
    # 进入端口视图（单个配置）
    print(f"\n[5/7] Configuring ports: {port_range}")
    
    # 解析端口范围: 25ge 1/0/31 to 25ge 1/0/36
    # 转换为单个端口列表
    ports = []
    if "to" in port_range:
        # 解析格式: "25ge 1/0/31 to 25ge 1/0/36"
        parts = port_range.split("to")
        start_port = parts[0].strip()
        end_port = parts[1].strip()
        
        # 提取接口号 - 格式: 25ge 1/0/31
        match = re.search(r'(25ge\s+\d+/\d+/)\d+', start_port)
        if match:
            interface_prefix = match.group(1)  # 25ge 1/0/
            
            # 提取起始和结束端口号
            start_match = re.search(r'/(\d+)$', start_port)
            end_match = re.search(r'/(\d+)$', end_port)
            
            if start_match and end_match:
                start_port_num = int(start_match.group(1))
                end_port_num = int(end_match.group(1))
                
                # 生成端口列表
                for i in range(start_port_num, end_port_num + 1):
                    ports.append(f"{interface_prefix}{i}")
    
    print(f"Ports to configure: {ports}")
    
    # 配置每个端口
    for port in ports:
        print(f"\n  Configuring {port}...")
        
        # 进入接口视图
        channel.send(f"interface {port}\n".encode())
        time.sleep(1)
        recv_all(channel, 2)
        
        # 设置速率为 10GE
        channel.send(b"speed 10000\n")
        time.sleep(1)
        output = recv_all(channel, 2)
        
        # 启用端口
        channel.send(b"undo shutdown\n")
        time.sleep(1)
        recv_all(channel, 2)
        
        # 设置描述
        channel.send(b"description 10GE_Migration\n")
        time.sleep(1)
        recv_all(channel, 2)
        
        # 退出接口视图
        channel.send(b"quit\n")
        time.sleep(1)
        recv_all(channel, 2)
        
        print(f"  {port} configured")
    
    # 保存配置
    print("\n[6/7] Saving configuration...")
    channel.send(b"save\n")
    time.sleep(2)
    output = recv_all(channel, 3)
    
    # 确认保存
    if "Y" in output or "confirm" in output.lower():
        channel.send(b"Y\n")
        time.sleep(3)
        recv_all(channel, 4)
    
    # 验证配置
    print("\n[7/7] Verifying configuration...")
    channel.send(b"display interface 25ge 1/0/31\n")
    time.sleep(2)
    output = recv_all(channel, 3)
    
    # 检查速度
    speed_match = re.search(r'Speed:\s+(\d+)', output)
    if speed_match:
        speed = speed_match.group(1)
        print(f"Port 25GE 1/0/31 speed: {speed}")
    
    # 退出
    channel.send(b"quit\n")
    time.sleep(1)
    client.close()
    
    print("\n" + "=" * 50)
    print("Configuration completed!")
    print("=" * 50)
    return True


def main():
    switch_ip = os.environ.get('SWITCH_IP', '')
    username = os.environ.get('SWITCH_USER', '')
    password = os.environ.get('SWITCH_PASS', '')
    port_range = os.environ.get('PORT_RANGE', '25ge 1/0/31 to 25ge 1/0/36')
    
    if not switch_ip or not username or not password:
        print("ERROR: Missing required parameters")
        print("Required: SWITCH_IP, SWITCH_USER, SWITCH_PASS")
        sys.exit(1)
    
    print(f"Parameters:")
    print(f"  SWITCH_IP: {switch_ip}")
    print(f"  SWITCH_USER: {username}")
    print(f"  PORT_RANGE: {port_range}")
    print()
    
    try:
        configure_huawei_switch(switch_ip, username, password, port_range)
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()