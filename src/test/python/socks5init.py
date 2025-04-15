import socket

def query_socks5_authentication(proxy_host, proxy_port):
    try:
        # Create a socket and connect to the SOCKS5 proxy
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((proxy_host, proxy_port))

        # Send SOCKS5 initial request
        # 0x05: SOCKS5 version
        # 0x02: Number of authentication methods
        # 0x00: No authentication
        # 0x02: Username/password authentication
        initial_request = b"\x05\x02\x00\x02"
        sock.sendall(initial_request)

        # Receive the response from the proxy
        response = sock.recv(2)

        # Parse the response
        if len(response) < 2:
            print("Invalid response from proxy")
            return

        version, auth_method = response
        if version != 0x05:
            print("Unsupported SOCKS version:", version)
            return

        # Print the accepted authentication method
        if auth_method == 0x00:
            print("Proxy accepts: No authentication")
        elif auth_method == 0x02:
            print("Proxy accepts: Username/password authentication")
        elif auth_method == 0xFF:
            print("Proxy accepts: No acceptable authentication methods")
        else:
            print(f"Proxy accepts: Unknown authentication method (0x{auth_method:02X})")

    except Exception as e:
        print("Error querying SOCKS5 authentication:", e)
    finally:
        sock.close()

# Query the SOCKS5 proxy
query_socks5_authentication("127.0.0.1", 2080)  # Replace with your proxy's IP and port