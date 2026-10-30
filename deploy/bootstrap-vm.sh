#!/usr/bin/env bash
# One-time setup of a fresh Oracle Cloud Always Free VM (Ubuntu 22.04 or 24.04, Arm Ampere A1) for Sijill.
# Run once as the default "ubuntu" user:   curl -fsSL <raw url of this file> | sudo bash -s -- "<deploy public key>"
# or copy it over and:                      sudo bash bootstrap-vm.sh "ssh-ed25519 AAAA... github-deploy"
#
# It installs Docker, adds 4 GB of swap, opens ports 80/443 in the VM's own firewall (Oracle images block them even
# when the cloud security list allows them), turns on automatic security updates, and creates a "deploy" user that
# GitHub Actions logs in as, owning /opt/sijill. It does not touch anything else.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run with sudo" >&2; exit 1; }
DEPLOY_KEY="${1:-}"
[ -n "$DEPLOY_KEY" ] || { echo "usage: $0 \"<public ssh key for GitHub Actions>\"" >&2; exit 2; }

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q ca-certificates curl python3 unattended-upgrades iptables-persistent rsync

if ! command -v docker >/dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Oracle's Ubuntu image ends INPUT with a REJECT rule; insert ACCEPTs above it.
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || iptables -I INPUT 5 -p tcp --dport "$port" -j ACCEPT
done
iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || iptables -I INPUT 5 -p udp --dport 443 -j ACCEPT
netfilter-persistent save

dpkg-reconfigure -f noninteractive unattended-upgrades

id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
grep -qxF "$DEPLOY_KEY" /home/deploy/.ssh/authorized_keys 2>/dev/null || echo "$DEPLOY_KEY" >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
install -d -m 750 -o deploy -g deploy /opt/sijill

echo "Done. Next: copy deploy/.env.example to /opt/sijill/.env as the deploy user and fill it in (docs/DEPLOYMENT.md)."
