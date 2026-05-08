#!/usr/bin/env python3
"""
Termux 环境下的浏览器截图工具
使用 Selenium + Chromium
"""

import sys
import time
import os
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

def take_screenshot(url, output_path, width=1280, height=800, wait_time=2):
    """访问URL并截图"""
    
    # Chrome 配置
    options = Options()
    options.binary_location = "/data/data/com.termux/files/usr/lib/chromium/chrome"
    options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument(f"--window-size={width},{height}")
    options.add_argument("--hide-scrollbars")
    
    # ChromeDriver 服务
    service = Service(executable_path="/data/data/com.termux/files/usr/bin/chromedriver")
    
    # 启动浏览器
    driver = webdriver.Chrome(service=service, options=options)
    
    try:
        # 访问页面
        driver.get(url)
        time.sleep(wait_time)
        
        # 截图
        driver.save_screenshot(output_path)
        print(f"截图已保存: {output_path}")
        
    finally:
        driver.quit()

def main():
    if len(sys.argv) < 3:
        print("用法: python3 screenshot.py <URL> <输出路径> [宽度] [高度] [等待秒数]")
        print("示例: python3 screenshot.py http://localhost:3001 screenshot.png 1280 800 2")
        sys.exit(1)
    
    url = sys.argv[1]
    output_path = sys.argv[2]
    width = int(sys.argv[3]) if len(sys.argv) > 3 else 1280
    height = int(sys.argv[4]) if len(sys.argv) > 4 else 800
    wait_time = int(sys.argv[5]) if len(sys.argv) > 5 else 2
    
    take_screenshot(url, output_path, width, height, wait_time)

if __name__ == "__main__":
    main()
