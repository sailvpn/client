# python
#!/usr/bin/env python3
import argparse
import os
import requests
import sys
import socket
import ssl
import urllib.parse
from urllib import request as urllib_request
from urllib.parse import urljoin

def normalize_proxy(proxy_input: str) -> str:
    if "://" in proxy_input:
        return proxy_input
    return "http://" + proxy_input

def probe_proxy_connect(proxy_url: str, target_host: str, target_port: int = 2080, timeout: float = 3.0):
    u = urllib.parse.urlparse(proxy_url)
    host = u.hostname
    port = u.port or (443 if u.scheme == "https" else 80)
    print(f"Using proxy: {u.geturl()} -> connecting to {host}:{port}", file=sys.stderr)
    try:
        raw = socket.create_connection((host, port), timeout=timeout)
        if u.scheme == "https":
            ctx = ssl.create_default_context()
            raw = ctx.wrap_socket(raw, server_hostname=host)
        # Probe with CONNECT to see proxy behavior (diagnostic only)
        connect_req = f"CONNECT {target_host}:{target_port} HTTP/1.1\r\nHost: {target_host}:{target_port}\r\n\r\n"
        raw.sendall(connect_req.encode("ascii"))
        raw.settimeout(2.0)
        resp = raw.recv(4096)
        first_line = resp.splitlines()[0] if resp else b"<no data>"
        print("Proxy probe response (first bytes):", first_line, file=sys.stderr)
        raw.close()
    except Exception as e:
        print("Proxy probe error:", repr(e), file=sys.stderr)

def dump_response(resp):
    try:
        print("Status:", resp.status_code, file=sys.stderr)
        print("Headers:", file=sys.stderr)
        for k, v in resp.headers.items():
            print(f"  {k}: {v}", file=sys.stderr)
        body = resp.text
        print("\nBody preview (first 1000 chars):\n", file=sys.stderr)
        print(body[:1000], file=sys.stderr)
    except Exception as e:
        print("Error dumping response:", repr(e), file=sys.stderr)

def make_request(session, url, verify, timeout, max_redirects):
    for attempt in range(max_redirects + 1):
        print(f"[attempt {attempt}] GET {url}", file=sys.stderr)
        resp = session.get(url, allow_redirects=False, verify=verify, timeout=timeout)
        print(f"[attempt {attempt}] received {resp.status_code}", file=sys.stderr)
        if resp.status_code == 307:
            loc = resp.headers.get("Location")
            print(f"[attempt {attempt}] 307 Location: {loc}", file=sys.stderr)
            print(f"[attempt {attempt}] Dumping 307 response from proxy:", file=sys.stderr)
            dump_response(resp)
            # attempt to surface the raw status line if available
            try:
                orig = getattr(resp.raw, "_original_response", None)
                if orig is not None:
                    print(f"[attempt {attempt}] raw status line: {orig.status} {orig.reason}", file=sys.stderr)
            except Exception:
                pass
            if not loc:
                raise RuntimeError("Received 307 but no Location header to follow")
            url = urljoin(url, loc)
            continue
        return resp
    raise RuntimeError(f"Too many redirects (>{max_redirects})")

def visit_with_urllib(url: str, proxy_url: str, cacert_path: str, is_https_target: bool):
    proxy_handler = urllib_request.ProxyHandler({
        "http": proxy_url,
        "https": proxy_url,
    })
    if is_https_target:
        ctx = ssl.create_default_context(cafile=cacert_path) if cacert_path else ssl.create_default_context()
        https_handler = urllib_request.HTTPSHandler(context=ctx)
        opener = urllib_request.build_opener(proxy_handler, https_handler)
    else:
        http_handler = urllib_request.HTTPHandler()
        opener = urllib_request.build_opener(proxy_handler, http_handler)
    with opener.open(url, timeout=10) as resp:
        body = resp.read()
        print(f"urllib -> {resp.getcode()}, len={len(body)}")
        print(body[:500].decode(errors="replace"))

def main():
    parser = argparse.ArgumentParser()
    # default changed to HTTP so the client does not attempt a direct TLS handshake to the proxy endpoint
    parser.add_argument("--url", default="http://127.0.0.1:5001", help="Target URL (default: http://127.0.0.1:5001)")
    parser.add_argument("--proxy", default="127.0.0.1:9999", help="Local HTTP relay (host:port)")
    parser.add_argument("--cacert", default="./certs/ca.crt", help="Path to CA bundle (used only for HTTPS targets)")
    parser.add_argument("--insecure", action="store_true", help="Disable TLS verification for HTTPS targets")
    parser.add_argument("--save")
    parser.add_argument("--max-redirects", type=int, default=5)
    args = parser.parse_args()

    url = args.url
    proxy_input = args.proxy
    proxy_url = normalize_proxy(proxy_input)

    # diagnostic probe (optional)
    probe_proxy_connect(proxy_url, "127.0.0.1", 5001)

    # resolve project-root relative default cacert
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, "..", "..", ".."))
    cacert_path = args.cacert
    if cacert_path == parser.get_default("cacert") and cacert_path.startswith("./"):
        cacert_path = os.path.normpath(os.path.join(project_root, cacert_path[2:]))
    if not os.path.isabs(cacert_path):
        cacert_path = os.path.abspath(cacert_path)

    is_https_target = url.lower().startswith("https://")
    verify = True
    if is_https_target:
        if args.insecure:
            verify = False
            from urllib3.exceptions import InsecureRequestWarning
            import urllib3
            urllib3.disable_warnings(InsecureRequestWarning)
        else:
            if os.path.isfile(cacert_path) and os.access(cacert_path, os.R_OK):
                verify = cacert_path
            else:
                try:
                    import certifi
                    system_ca = certifi.where()
                    print(f"Warning: CA `{args.cacert}` not found; falling back to system CA `{system_ca}`.", file=sys.stderr)
                    verify = system_ca
                    cacert_path = system_ca
                except Exception:
                    print(f"Cannot find CA `{args.cacert}`. Use `--insecure` or provide a valid `--cacert`.", file=sys.stderr)
                    sys.exit(2)
    else:
        # for plain HTTP target, TLS verification is not applicable
        verify = True

    proxies = {"http": proxy_url, "https": proxy_url}
    session = requests.Session()
    session.trust_env = False
    session.proxies.update(proxies)

    try:
        resp = make_request(session, url, verify=verify, timeout=15, max_redirects=args.max_redirects)
    except requests.exceptions.SSLError as e:
        print("SSL error:", repr(e), file=sys.stderr)
        sys.exit(2)
    except requests.exceptions.RequestException as e:
        print("Request error:", repr(e), file=sys.stderr)
        resp = getattr(e, "response", None)
        if resp is not None:
            print("Attached response from exception:", file=sys.stderr)
            dump_response(resp)
        sys.exit(1)
    except RuntimeError as e:
        print("Error:", e, file=sys.stderr)
        sys.exit(3)

    dump_response(resp)
    # also try urllib path (diagnostic)
    try:
        visit_with_urllib(url, proxy_url, cacert_path if is_https_target else None, is_https_target)
    except Exception as e:
        print("urllib error:", repr(e), file=sys.stderr)

    if args.save:
        with open(args.save, "w", encoding=resp.encoding or "utf-8") as f:
            f.write(resp.text)
        print(f"\nSaved full response to {args.save}", file=sys.stderr)

if __name__ == "__main__":
    main()