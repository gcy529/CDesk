#!/usr/bin/env python3
"""
CDesk 正版授权服务端。

按客户端签名指纹下发 RSA 签名授权令牌。客户端用内置公钥验签，
验签通过且令牌未过期、签名指纹匹配才放行。

授权逻辑：
  - license_auth.json 里按 fp 登记授权条目（官方签名 + 各定制客户签名）
  - 请求带 ?fp=<签名指纹>，fp 在登记列表里才发令牌，否则返回空 {}
  - 令牌用 server.key 做 SHA256withRSA 签名，防伪造

部署：
  1. 把 server.key 放到本脚本同目录（私钥不进仓库）
  2. 编辑 license_auth.json，登记授权指纹及到期时间
  3. python3 license_server.py
  4. 客户端 LICENSE_ENDPOINT 指向 http://<服务器>:8765/v1/license

无第三方依赖，签名走 openssl 命令行，仅需 Python3 + openssl。
"""

import base64
import http.server
import json
import os
import subprocess
import sys
import time
from urllib.parse import urlparse, parse_qs

HOST = "0.0.0.0"
PORT = 8765
BASE = os.path.dirname(os.path.abspath(__file__))
PRIVATE_KEY = os.path.join(BASE, "server.key")
AUTH_CONFIG = os.path.join(BASE, "license_auth.json")


def load_auth():
    if not os.path.exists(AUTH_CONFIG):
        return {}
    with open(AUTH_CONFIG, "r", encoding="utf-8") as f:
        return json.load(f)


def sign(payload):
    """用 openssl 私钥对 payload 做 SHA256withRSA 签名，返回 base64。"""
    proc = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", PRIVATE_KEY],
        input=payload.encode("utf-8"),
        capture_output=True,
        check=True,
    )
    return base64.b64encode(proc.stdout).decode("ascii")


class LicenseHandler(http.server.BaseHTTPRequestHandler):

    def do_GET(self):
        if not self.path.startswith("/v1/license"):
            self.send_error(404, "not found")
            return
        qs = parse_qs(urlparse(self.path).query)
        fp = qs.get("fp", [""])[0]
        entry = load_auth().get(fp)
        if not entry:
            self._json({})
            return
        cid = entry.get("cid", "")
        exp = int(entry.get("exp", time.time() + 365 * 24 * 3600))
        scope = entry.get("scope", "full")
        payload = "{}|{}|{}|{}".format(cid, exp, scope, fp)
        token = {
            "cid": cid,
            "exp": exp,
            "scope": scope,
            "fp": fp,
            "sig": sign(payload),
        }
        self._json(token)

    def _json(self, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        sys.stderr.write("[license] %s - %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    if not os.path.exists(PRIVATE_KEY):
        print("错误：找不到私钥 %s，请把 server.key 放到本脚本同目录" % PRIVATE_KEY, file=sys.stderr)
        sys.exit(1)
    print("CDesk 授权服务已启动: http://%s:%d/v1/license" % (HOST, PORT))
    httpd = http.server.HTTPServer((HOST, PORT), LicenseHandler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
