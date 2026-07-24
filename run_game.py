#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import http.server
import socketserver
import webbrowser
import threading
import time

PORT = 8000
DIRECTORY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "assets", "www")

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

def open_browser():
    # Wait a moment for server to spin up
    time.sleep(1)
    url = f"http://localhost:{PORT}/index.html"
    print(f"\n[+] 正在为您打开网页：{url}")
    webbrowser.open(url)

def main():
    if not os.path.exists(DIRECTORY):
        print(f"[-] 错误：目录 {DIRECTORY} 不存在！", file=sys.stderr)
        sys.exit(1)
        
    print("[+] 智能神经网络五子棋人机大战本地服务器正在启动...")
    print(f"[+] 游戏资源路径：{DIRECTORY}")
    
    # Run browser opening in a separate thread
    threading.Thread(target=open_browser, daemon=True).start()
    
    # Start simple HTTP server
    socketserver.TCPServer.allow_reuse_address = True
    try:
        with socketserver.TCPServer(("", PORT), Handler) as httpd:
            print(f"[+] 服务器启动成功！端口：{PORT}")
            print("[+] 按 Ctrl+C 可以随时停止服务器。")
            httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[+] 服务器已安全关闭。感谢您的游玩！")
    except Exception as e:
        print(f"[-] 服务器启动失败：{e}", file=sys.stderr)

if __name__ == "__main__":
    main()
