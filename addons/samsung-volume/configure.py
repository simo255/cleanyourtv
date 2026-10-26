#!/usr/bin/env python3
"""Configure the locally built add-on. Never prints addresses, pins or pairing keys."""
import argparse
import datetime
import hashlib
import ipaddress
import pathlib
import socket
import ssl
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, help="Path to the trusted Android platform-tools adb")
    parser.add_argument("--device", required=True, help="Authorized onn device endpoint")
    parser.add_argument("--tv-host", required=True, help="Local Samsung IPv4 address")
    args = parser.parse_args()
    host = ipaddress.IPv4Address(args.tv_host)
    if not (host.is_private and not host.is_loopback and not host.is_unspecified and not host.is_multicast):
        parser.error("Use the television's local IPv4 address")
    package = "com.cleanyourtv.samsungvolume"

    def adb(*command):
        result = subprocess.run([args.adb, "-s", args.device, *command],
                                capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError("ADB operation failed; check the device and authorization")
        return result.stdout

    if adb("get-state").strip() != b"device":
        raise RuntimeError("The box is not available")
    if not adb("shell", "pm", "path", package).strip().startswith(b"package:"):
        raise RuntimeError("Build and install Samsung Volume first")
    root = pathlib.Path(__file__).resolve().parents[2]
    backup = root / "backup" / ("samsung-volume-setup-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    backup.mkdir(parents=True)
    for name, command in {
        "packages": ["pm", "list", "packages"],
        "disabled": ["pm", "list", "packages", "-d"],
        "getprop": ["getprop"],
        "settings-system": ["settings", "list", "system"],
        "settings-secure": ["settings", "list", "secure"],
        "settings-global": ["settings", "list", "global"],
    }.items():
        (backup / (name + ".txt")).write_bytes(adb("shell", *command))

    # Initial local enrollment: learn this TV's leaf certificate, then pin all app connections to it.
    # Samsung's on-screen approval remains mandatory; this is not a bypass of pairing.
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    with socket.create_connection((str(host), 8002), timeout=5) as raw:
        with context.wrap_socket(raw, server_hostname=str(host)) as connection:
            pin = hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest()
    response = adb("shell", "am", "broadcast", "-n", package + "/.SetupReceiver",
                   "-a", package + ".CONFIGURE", "--es", "host", str(host), "--es", "cert_pin", pin)
    if b"Broadcast completed" not in response:
        raise RuntimeError("The setup receiver did not acknowledge configuration")
    adb("shell", "am", "start", "-n", package + "/.MainActivity")
    print("TV configured. Approve onn Volume on the Samsung, then enable the accessibility service.")
    print("Keep volume control on Automatic (CEC). Approve the box's local debugging request when shown.")
    print("Private setup backup saved under backup/.")


if __name__ == "__main__":
    main()
