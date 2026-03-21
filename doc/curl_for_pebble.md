# 1) Skip proxy for 127.0.0.1 only
curl -vk --noproxy 127.0.0.1 --tlsv1.2 --http1.1 https://127.0.0.1:15000/roots/0

# 2) Skip proxy for common local hosts
curl -vk --noproxy 'localhost,127.0.0.1,::1' https://127.0.0.1:15000/roots/0

# 3) Disable proxy for the single curl invocation
curl -vk --proxy "" https://127.0.0.1:15000/roots/0

# 4) Run curl without inheriting proxy env vars (bash)
env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY curl -vk https://127.0.0.1:15000/roots/0

# 5) Persistently skip proxy in your shell
export NO_PROXY=localhost,127.0.0.1,::1
# (or set uppercase NO_PROXY/NO_PROXY depending on tools)


