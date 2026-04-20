#!/usr/bin/env python3
"""
Selenium-based browser helper for Termux/Android
Usage: python3 browser_helper.py <command> [args...]

Commands:
  open <url>           - Open URL and get title
  screenshot <file>    - Take screenshot
  html                 - Get page source
  click <selector>     - Click element by CSS selector
  type <selector> <text> - Type text into element
  wait <selector>      - Wait for element
  evaluate <js>        - Execute JavaScript
"""

import sys
import json
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

CHROME_BINARY = "/data/data/com.termux/files/usr/lib/chromium/chrome"
CHROMEDRIVER = "/data/data/com.termux/files/usr/lib/chromium/chromedriver"

_driver = None

def get_driver():
    global _driver
    if _driver is None:
        options = Options()
        options.binary_location = CHROME_BINARY
        options.add_argument("--headless=new")
        options.add_argument("--no-sandbox")
        options.add_argument("--disable-gpu")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--window-size=1920,1080")
        
        service = Service(executable_path=CHROMEDRIVER)
        _driver = webdriver.Chrome(service=service, options=options)
    return _driver

def close_driver():
    global _driver
    if _driver:
        _driver.quit()
        _driver = None

def cmd_open(url):
    driver = get_driver()
    driver.get(url)
    return {"title": driver.title, "url": driver.current_url}

def cmd_screenshot(filename):
    driver = get_driver()
    driver.save_screenshot(filename)
    return {"saved": filename}

def cmd_html():
    driver = get_driver()
    return {"html": driver.page_source[:50000]}  # Limit size

def cmd_click(selector):
    driver = get_driver()
    element = WebDriverWait(driver, 10).until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, selector))
    )
    element.click()
    return {"clicked": selector}

def cmd_type(selector, text):
    driver = get_driver()
    element = WebDriverWait(driver, 10).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, selector))
    )
    element.clear()
    element.send_keys(text)
    return {"typed": text, "selector": selector}

def cmd_wait(selector, timeout=10):
    driver = get_driver()
    WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, selector))
    )
    return {"found": selector}

def cmd_evaluate(js_code):
    driver = get_driver()
    result = driver.execute_script(js_code)
    return {"result": str(result)[:5000]}

def cmd_text(selector):
    driver = get_driver()
    element = driver.find_element(By.CSS_SELECTOR, selector)
    return {"text": element.text}

def cmd_find(selector):
    driver = get_driver()
    elements = driver.find_elements(By.CSS_SELECTOR, selector)
    return {
        "count": len(elements),
        "texts": [el.text[:200] for el in elements[:20]]
    }

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No command provided"}))
        return
    
    command = sys.argv[1]
    
    try:
        if command == "open" and len(sys.argv) >= 3:
            result = cmd_open(sys.argv[2])
        elif command == "screenshot" and len(sys.argv) >= 3:
            result = cmd_screenshot(sys.argv[2])
        elif command == "html":
            result = cmd_html()
        elif command == "click" and len(sys.argv) >= 3:
            result = cmd_click(sys.argv[2])
        elif command == "type" and len(sys.argv) >= 4:
            result = cmd_type(sys.argv[2], sys.argv[3])
        elif command == "wait" and len(sys.argv) >= 3:
            result = cmd_wait(sys.argv[2])
        elif command == "evaluate" and len(sys.argv) >= 3:
            result = cmd_evaluate(sys.argv[2])
        elif command == "text" and len(sys.argv) >= 3:
            result = cmd_text(sys.argv[2])
        elif command == "find" and len(sys.argv) >= 3:
            result = cmd_find(sys.argv[2])
        elif command == "quit":
            close_driver()
            result = {"status": "quit"}
        else:
            result = {"error": f"Unknown command or missing args: {command}"}
        
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except Exception as e:
        print(json.dumps({"error": str(e)}))

if __name__ == "__main__":
    main()
