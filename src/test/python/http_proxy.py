# python
# File: `visit_via_proxy.py`
from urllib import request as urllib_request

def visit_with_requests(url: str, proxy_url: str):
    import requests
    proxies = {
        "http": proxy_url,
        "https": proxy_url,
    }
    resp = requests.get(url, proxies=proxies, timeout=10)
    print(f"requests -> {resp.status_code}, len={len(resp.content)}")
    print(resp.text[:500])

def visit_with_urllib(url: str, proxy_url: str):
    proxy_handler = urllib_request.ProxyHandler({
        "http": proxy_url,
        "https": proxy_url,
    })
    opener = urllib_request.build_opener(proxy_handler)
    with opener.open(url, timeout=10) as resp:
        body = resp.read()
        print(f"urllib -> {resp.getcode()}, len={len(body)}")
        print(body[:500].decode(errors="replace"))

if __name__ == "__main__":

    url = "https://sina.com.cn"
    proxy = "127.0.0.1:9999"

    try:
        visit_with_requests(url, proxy)
    except Exception as e:
        print("requests error:", e)

    try:
        visit_with_urllib(url, proxy)
    except Exception as e:
        print("urllib error:", e)