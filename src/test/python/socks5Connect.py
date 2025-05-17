import socket

def test_socks5_connect(proxy_host, proxy_port, target_host, target_port):
    try:
        # Create a socket and connect to the SOCKS5 proxy
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((proxy_host, proxy_port))

        # Send SOCKS5 initial request (no authentication)
        initial_request = b"\x05\x01\x00"  # SOCKS5, 1 auth method, no authentication
        sock.sendall(initial_request)

        # Receive the response from the proxy
        response = sock.recv(2)
        if len(response) < 2 or response[0] != 0x05 or response[1] != 0x00:
            print("SOCKS5 handshake failed")
            return

        # Send SOCKS5 connection request
        # 0x05: SOCKS5 version
        # 0x01: CONNECT command
        # 0x00: Reserved
        # 0x03: Address type (domain name)
        # Followed by the target host length, target host, and target port
        target_host_bytes = target_host.encode('utf-8')
        target_port_bytes = target_port.to_bytes(2, 'big')
        connect_request = (
            b"\x05\x01\x00\x03" +
            len(target_host_bytes).to_bytes(1, 'big') +
            target_host_bytes +
            target_port_bytes
        )
        sock.sendall(connect_request)

        # Receive the response from the proxy
        response = sock.recv(10)
        if len(response) < 10 or response[1] != 0x00:
            print("SOCKS5 connection failed")
            return

        print("SOCKS5 connection successful")

    except Exception as e:
        print("Error testing SOCKS5 connection:", e)
    finally:
        sock.close()

# Test the SOCKS5 proxy connection
test_socks5_connect("127.0.0.1", 3080, "sina.com.cn", 80)  # Replace with your proxy and target details