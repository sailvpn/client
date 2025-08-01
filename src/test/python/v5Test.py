import socks
import socket
import requests

# Test SOCKS5 proxy without authentication
try:
    print("Testing SOCKS5 proxy without authentication...")
    socks.set_default_proxy(socks.SOCKS5, "127.0.0.1", 2080)  # Replace with your proxy's IP and port
    socket.socket = socks.socksocket

    # Send a request through the proxy
    response = requests.get("http://example.com")
    print("Status Code:", response.status_code)
    print("Response Body:", response.text[:200])  # Print the first 200 characters of the response
except Exception as e:
    print("Error (No Authentication):", e)

# Test SOCKS5 proxy with authentication
try:
    print("\nTesting SOCKS5 proxy with authentication...")
    socks.set_default_proxy(socks.SOCKS5, "127.0.0.1", 2080, username="user", password="pass")
    socket.socket = socks.socksocket

    # Send a request through the proxy
    response = requests.get("http://example.com")
    print("Status Code:", response.status_code)
    print("Response Body:", response.text[:200])  # Print the first 200 characters of the response
except Exception as e:
    print("Error (With Authentication):", e)  
    
    