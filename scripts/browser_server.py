#!/usr/bin/env python3
"""
Persistent Selenium browser server for Termux/Android
Runs as a background process, accepts commands via stdin (JSON)

Usage:
  # Start server (background)
  python3 browser_server.py &
  
  # Send commands
  echo '{"cmd":"open","url":"https://example.com"}' | nc -U /tmp/browser.sock
  
Or use the simple HTTP API:
  python3 browser_server.py --http 8765
  curl http://localhost:8765/open?url=https://example.com
"""

import sys
import json
import os
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

CHROME_BINARY = "/data/data/com.termux/files/usr/lib/chromium/chrome"
CHROMEDRIVER = "/data/data/com.termux/files/usr/lib/chromium/chromedriver"

class BrowserSession:
    def __init__(self):
        self.driver = None
    
    def get_driver(self):
        if self.driver is None:
            options = Options()
            options.binary_location = CHROME_BINARY
            options.add_argument("--headless=new")
            options.add_argument("--no-sandbox")
            options.add_argument("--disable-gpu")
            options.add_argument("--disable-dev-shm-usage")
            options.add_argument("--window-size=1920,1080")
            
            service = Service(executable_path=CHROMEDRIVER)
            self.driver = webdriver.Chrome(service=service, options=options)
        return self.driver
    
    def close(self):
        if self.driver:
            self.driver.quit()
            self.driver = None
    
    def execute(self, cmd, **kwargs):
        driver = self.get_driver()
        
        if cmd == "open":
            driver.get(kwargs.get("url", "about:blank"))
            return {"title": driver.title, "url": driver.current_url}
        
        elif cmd == "screenshot":
            path = kwargs.get("path", "/tmp/screenshot.png")
            driver.save_screenshot(path)
            return {"saved": path}
        
        elif cmd == "html":
            html = driver.page_source
            max_len = kwargs.get("max_len", 50000)
            return {"html": html[:max_len], "truncated": len(html) > max_len}
        
        elif cmd == "title":
            return {"title": driver.title, "url": driver.current_url}
        
        elif cmd == "click":
            selector = kwargs.get("selector")
            timeout = kwargs.get("timeout", 10)
            element = WebDriverWait(driver, timeout).until(
                EC.element_to_be_clickable((By.CSS_SELECTOR, selector))
            )
            element.click()
            return {"clicked": selector}
        
        elif cmd == "type":
            selector = kwargs.get("selector")
            text = kwargs.get("text", "")
            element = WebDriverWait(driver, 10).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, selector))
            )
            element.clear()
            element.send_keys(text)
            return {"typed": text}
        
        elif cmd == "submit":
            selector = kwargs.get("selector")
            element = driver.find_element(By.CSS_SELECTOR, selector)
            element.submit()
            return {"submitted": selector}
        
        elif cmd == "wait":
            selector = kwargs.get("selector")
            timeout = kwargs.get("timeout", 10)
            WebDriverWait(driver, timeout).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, selector))
            )
            return {"found": selector}
        
        elif cmd == "find":
            selector = kwargs.get("selector")
            elements = driver.find_elements(By.CSS_SELECTOR, selector)
            return {
                "count": len(elements),
                "elements": [
                    {"tag": el.tag_name, "text": el.text[:200], "id": el.get_attribute("id")}
                    for el in elements[:20]
                ]
            }
        
        elif cmd == "evaluate":
            js = kwargs.get("js", "return null")
            result = driver.execute_script(js)
            return {"result": str(result)[:5000] if result else None}
        
        elif cmd == "back":
            driver.back()
            return {"status": "back"}
        
        elif cmd == "forward":
            driver.forward()
            return {"status": "forward"}
        
        elif cmd == "refresh":
            driver.refresh()
            return {"status": "refreshed"}
        
        elif cmd == "quit":
            self.close()
            return {"status": "quit"}
        
        else:
            return {"error": f"Unknown command: {cmd}"}


# Global session
session = BrowserSession()

class BrowserHTTPHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.lstrip("/")
        params = parse_qs(parsed.query)
        
        # Flatten single values
        kwargs = {k: v[0] if len(v) == 1 else v for k, v in params.items()}
        
        try:
            if path in ["open", "screenshot", "html", "title", "click", "type", 
                       "submit", "wait", "find", "evaluate", "back", "forward", 
                       "refresh", "quit", "status"]:
                if path == "status":
                    result = {"status": "running", "url": session.get_driver().current_url}
                else:
                    result = session.execute(path, **kwargs)
            else:
                result = {"error": f"Unknown path: {path}"}
        except Exception as e:
            result = {"error": str(e)}
        
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(result, ensure_ascii=False).encode())
    
    def log_message(self, format, *args):
        pass  # Suppress logging


def run_http_server(port):
    server = HTTPServer(("127.0.0.1", port), BrowserHTTPHandler)
    print(f"Browser server running on http://127.0.0.1:{port}", file=sys.stderr)
    server.serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Browser server")
    parser.add_argument("--http", type=int, help="Run HTTP server on port")
    args = parser.parse_args()
    
    if args.http:
        run_http_server(args.http)
    else:
        # Simple stdin/stdout mode
        for line in sys.stdin:
            try:
                req = json.loads(line)
                cmd = req.pop("cmd", "status")
                result = session.execute(cmd, **req)
            except Exception as e:
                result = {"error": str(e)}
            print(json.dumps(result, ensure_ascii=False))
            sys.stdout.flush()
