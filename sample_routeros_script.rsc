# ======================================================
# Rainet OSS/BSS - RouterOS Configuration
# ======================================================
# Router: demo-rb4011
# Management IP: 192.168.1.1
# Tenant ID: 123e4567-e89b-12d3-a456-426614174000
# Generated: 2026-01-06 23:58:30
# Config Version: 1.0
# ======================================================
# WARNING: This script is idempotent and safe to re-apply
# ======================================================

# Interface Configuration
# Idempotent: 'set' commands can be reapplied safely
/interface
:if ([/interface print count-only where name="wan"] = 0) do={
  set [find name=ether1] name=wan comment="WAN Interface"
}
:if ([/interface print count-only where name="lan"] = 0) do={
  set [find name=ether2] name=lan comment="LAN Interface"
}


# Bridge Configuration
# Idempotent: checks existence before creating
/interface/bridge
:if ([/interface/bridge print count-only where name="bridge-lan"] = 0) do={
  add name=bridge-lan comment="LAN Bridge"
}
/interface/bridge/port
:if ([/interface/bridge/port print count-only where interface="lan"] = 0) do={
  add interface=lan bridge=bridge-lan
}


# WAN Interface Configuration
# Idempotent: removes existing before adding
/ip/dns
set allow-remote-requests=yes
set servers=8.8.8.8,8.8.4.4


# LAN Configuration
# Idempotent: removes existing before adding
/ip/address
:if ([/ip/address print count-only where interface="bridge-lan" comment="LAN Network"] > 0) do={
  /ip/address remove [find interface="bridge-lan" comment="LAN Network"]
}
add address=192.168.88.1/24 interface=bridge-lan comment="LAN Network"
/ip/pool
:if ([/ip/pool print count-only where name="lan-pool"] > 0) do={
  /ip/pool remove [find name="lan-pool"]
}
add name=lan-pool ranges=192.168.88.10-192.168.88.254
/ip/dhcp-server
:if ([/ip/dhcp-server print count-only where name="lan-dhcp"] > 0) do={
  /ip/dhcp-server remove [find name="lan-dhcp"]
}
add name=lan-dhcp interface=bridge-lan address-pool=lan-pool disabled=no
/ip/dhcp-server/network
:if ([/ip/dhcp-server/network print count-only where address="192.168.88.1/24"] > 0) do={
  /ip/dhcp-server/network remove [find address="192.168.88.1/24"]
}
add address=192.168.88.1/24 gateway=192.168.88.1 dns-server=8.8.8.8 comment="LAN DHCP Network"


# PPPoE Server Configuration
# Idempotent: removes existing before adding
/ip/pool
:if ([/ip/pool print count-only where name="pppoe-pool-basic-10mb"] > 0) do={
  /ip/pool remove [find name="pppoe-pool-basic-10mb"]
}
add name=pppoe-pool-basic-10mb ranges=10.10.1.1-10.10.1.254 comment="Pool for basic-10mb"
:if ([/ip/pool print count-only where name="pppoe-pool-standard-50mb"] > 0) do={
  /ip/pool remove [find name="pppoe-pool-standard-50mb"]
}
add name=pppoe-pool-standard-50mb ranges=10.10.2.1-10.10.2.254 comment="Pool for standard-50mb"
:if ([/ip/pool print count-only where name="pppoe-pool-premium-100mb"] > 0) do={
  /ip/pool remove [find name="pppoe-pool-premium-100mb"]
}
add name=pppoe-pool-premium-100mb ranges=10.10.3.1-10.10.3.254 comment="Pool for premium-100mb"
/ppp/profile
:if ([/ppp/profile print count-only where name="pppoe-profile-basic-10mb"] > 0) do={
  /ppp/profile remove [find name="pppoe-profile-basic-10mb"]
}
add name=pppoe-profile-basic-10mb local-address=10.10.1.1 remote-address=pppoe-pool-basic-10mb rate-limit=5M/10M comment="Profile for basic-10mb plan"
:if ([/ppp/profile print count-only where name="pppoe-profile-standard-50mb"] > 0) do={
  /ppp/profile remove [find name="pppoe-profile-standard-50mb"]
}
add name=pppoe-profile-standard-50mb local-address=10.10.2.1 remote-address=pppoe-pool-standard-50mb rate-limit=25M/50M comment="Profile for standard-50mb plan"
:if ([/ppp/profile print count-only where name="pppoe-profile-premium-100mb"] > 0) do={
  /ppp/profile remove [find name="pppoe-profile-premium-100mb"]
}
add name=pppoe-profile-premium-100mb local-address=10.10.3.1 remote-address=pppoe-pool-premium-100mb rate-limit=50M/100M comment="Profile for premium-100mb plan"
/interface/pppoe-server/server
:if ([/interface/pppoe-server/server print count-only where interface="lan"] > 0) do={
  /interface/pppoe-server/server remove [find interface="lan"]
}
add service-name=rainet-isp interface=lan disabled=no one-session-per-host=yes max-mru=1480 max-mtu=1480
/ppp/aaa
set use-radius=yes
set interim-update=1m
set accounting=yes
/radius
:if ([/radius print count-only where address="127.0.0.1" service=ppp] > 0) do={
  /radius remove [find address="127.0.0.1" service=ppp]
}
add service=ppp address=127.0.0.1 secret=rainet-radius-secret timeout=3s


# Services Configuration
/system/ntp/client
set enabled=yes
set server=pool.ntp.org
/system/identity
set name=demo-rb4011
/system/logging
add topics=info action=memory
add topics=warning action=memory
add topics=error action=memory
/ip/service
set telnet disabled=yes
set ftp disabled=yes
set www disabled=yes
set ssh disabled=no
set api disabled=yes
set api-ssl disabled=yes


# Firewall Configuration
# Idempotent: removes rules by comment before adding
/ip/firewall/connection-tracking
set enabled=yes
set tcp-timeout=23h
set udp-timeout=10m
/ip/firewall/filter
:foreach rule in=[find comment~"Rainet:"] do={ /ip/firewall/filter remove $rule }
add action=drop chain=forward connection-state=invalid comment="Rainet: Drop invalid"
add action=accept chain=forward connection-state=established,related comment="Rainet: Accept established/related"
add action=accept chain=forward in-interface=bridge-lan out-interface=wan comment="Rainet: Accept from LAN"
add action=drop chain=forward comment="Rainet: Drop other forward"
add action=accept chain=input connection-state=established,related comment="Rainet: Accept input established/related"
add action=accept chain=input protocol=icmp comment="Rainet: Accept ICMP"
add action=accept chain=input in-interface=!wan comment="Rainet: Accept from local"
add action=drop chain=input comment="Rainet: Drop other input"

/ip/firewall/nat
:foreach rule in=[find comment~"Rainet:"] do={ /ip/firewall/nat remove $rule }
add action=masquerade chain=srcnat out-interface=wan comment="Rainet: Masquerade WAN"


# Quality of Service Configuration
# Idempotent: removes rules by comment before adding
/queue/tree
:foreach rule in=[find comment~"Rainet:"] do={ /queue/tree remove $rule }
add name=global-root parent=global-out max-limit=1000M comment="Rainet: Global bandwidth"
/queue/type
:foreach rule in=[find comment~"Rainet:"] do={ /queue/type remove $rule }
add name=high-priority kind=pcq pcq-rate=100M priority=1 comment="Rainet: QoS profile"
add name=normal-priority kind=pcq pcq-rate=50M priority=4 comment="Rainet: QoS profile"
add name=low-priority kind=pcq pcq-rate=10M priority=8 comment="Rainet: QoS profile"
/ip/firewall/mangle
:foreach rule in=[find comment~"Rainet: QoS"] do={ /ip/firewall/mangle remove $rule }
add action=mark-connection chain=forward in-interface=wan out-interface=bridge-lan new-connection-mark=download comment="Rainet: QoS Mark download"
add action=mark-connection chain=forward in-interface=bridge-lan out-interface=wan new-connection-mark=upload comment="Rainet: QoS Mark upload"


# ======================================================
# Script generation complete!
# 
# This script can be imported to RouterOS using:
# 1. Terminal: /import file=config.rsc
# 2. WinBox: Files menu, drag and drop, then import
# 3. API: Upload via FTP then execute import
# 
# The script is IDEMPOTENT - safe to reapply multiple times
# ======================================================
