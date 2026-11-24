"""Scan BLE for Yolanda/QN broadcast scales and print their manufacturer data, decoding the weight.

Run: uv run --with bleak python tools/scan_scale.py [seconds]   (default 180)
On macOS run it from Terminal.app so it gets Bluetooth permission.
A supported scale shows lines like `mfr 0xffff: aa bb ...`, followed by `-> weight 67.65 kg`.
"""
import asyncio, sys
from bleak import BleakScanner

SECS = int(sys.argv[1]) if len(sys.argv) > 1 else 180
HINTS = ("qn", "yolanda", "feelfit", "renpho", "scale")
QN_SERVICES = ("0000ffe0", "0000fff0", "0000ae00")
seen = {}

def cb(dev, adv):
    name = (adv.local_name or dev.name or "").lower()
    uuids = [u.lower() for u in adv.service_uuids]
    hit = any(h in name for h in HINTS) or any(u.startswith(QN_SERVICES) for u in uuids) or 0xFFFF in adv.manufacturer_data
    if not hit:
        return
    key = (dev.address, tuple((k, v.hex()) for k, v in adv.manufacturer_data.items()))
    if key in seen:
        return
    seen[key] = 1
    print(f"{dev.address} name={adv.local_name!r} rssi={adv.rssi} services={uuids}", flush=True)
    for cid, data in adv.manufacturer_data.items():
        line = f"  mfr 0x{cid:04x}: {data.hex(' ')}"
        if cid == 0xFFFF and len(data) >= 19 and data[:2] == b"\xaa\xbb":  # openScale QN broadcast layout
            line += f"  -> weight {int.from_bytes(data[17:19], 'little') / 100:.2f} kg, stable={data[15]}"
        print(line, flush=True)

async def main():
    async with BleakScanner(cb):
        await asyncio.sleep(SECS)
    print(f"done, {len(seen)} distinct adverts", flush=True)

asyncio.run(main())
