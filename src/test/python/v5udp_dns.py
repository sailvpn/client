import socket
import struct

PROXY_HOST = '127.0.0.1'
PROXY_PORT = 3080

# TARGET_HOST = '8.8.8.8'
# TARGET_PORT = 53
# DNS_QUERY = b'\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x03www\x06google\x03com\x00\x00\x01\x00\x01'
TARGET_HOST = '127.0.0.1'
TARGET_PORT = 5005
DNS_QUERY = "Hello, UDP Server!".encode('utf-8')

# SOCKS5 UDP request header
# +----+------+------+----------+----------+----------+
# |RSV | FRAG | ATYP | DST.ADDR | DST.PORT |   DATA   |
# +----+------+------+----------+----------+----------+
# | 2  |  1   |  1   | Variable |    2     | Variable |
# +----+------+------+----------+----------+----------+

def socks5_udp_associate(proxy_host, proxy_port):
    # 1. Connect to SOCKS5 proxy
    s = socket.create_connection((proxy_host, proxy_port))
    # 2. Handshake (no authentication)
    s.sendall(b'\x05\x01\x00')
    if s.recv(2) != b'\x05\x00':
        raise Exception('SOCKS5 handshake failed')
    # 3. UDP ASSOCIATE request
    s.sendall(b'\x05\x03\x00\x01\x00\x00\x00\x00\x00\x00')
    resp = s.recv(10)
    if resp[:2] != b'\x05\x00':
        raise Exception('SOCKS5 UDP associate failed')
    # 4. Parse relay address
    atyp = resp[3]
    if atyp == 1:  # IPv4
        relay_ip = socket.inet_ntoa(resp[4:8])
        relay_port = struct.unpack('>H', resp[8:10])[0]
    else:
        raise Exception('Only IPv4 relay supported in this example')
    s.close()
    return relay_ip, relay_port

def socks5_udp_packet(ip, port, data):
    rsv = b'\x00\x00'
    frag = b'\x00'
    atyp = b'\x01'
    addr = socket.inet_aton(ip)
    port_bytes = struct.pack('>H', port)
    return rsv + frag + atyp + addr + port_bytes + data

def main():
    relay_ip, relay_port = socks5_udp_associate(PROXY_HOST, PROXY_PORT)
    print(f'UDP relay at {relay_ip}:{relay_port}')
    udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    # set a much longer timer when debug, so the UDP channel won't close
    udp_sock.settimeout(600)
    # udp_sock.settimeout(5)
    packet = socks5_udp_packet(TARGET_HOST, TARGET_PORT, DNS_QUERY)
    udp_sock.sendto(packet, (relay_ip, relay_port))
    try:
        data, addr = udp_sock.recvfrom(4096)
        print(f'Response from {addr}: {data.hex()}')
    except socket.timeout:
        print('No response received (timeout)')
    udp_sock.close()

if __name__ == '__main__':
    main()