#!/usr/bin/env python3
"""Sync all environment *.properties files to share the same keys; test.properties is the value master."""

from pathlib import Path

ENV_DIR = Path(__file__).resolve().parents[1] / "src" / "test" / "resources" / "environments"
ENVS = ["test", "dev", "stage", "load", "prod"]


def parse_props(text: str) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value
    return props


def load_env(name: str) -> dict[str, str]:
    return parse_props((ENV_DIR / f"{name}.properties").read_text(encoding="utf-8"))


OVERRIDES: dict[str, dict[str, str]] = {
    "dev": {
        "base.url": "https://reqres.in/api",
        "vrgo.base.url": "https://api.vrgo.dev.xp.irdeto.com",
        "vrgo.auth.token.url": "https://consumer-am.dev.xp.irdeto.com/v1/auth/token",
        "vrgo.header.origin": "https://web.vrgo.dev.xp.irdeto.com",
        "vrgo.header.referer": "https://web.vrgo.dev.xp.irdeto.com/",
        "vrgo.header.catalogueids": (
            "ottlive,otttest,weblive,webtest,crmlive,crmtest,"
            "ottkidslive,ottkidstest,webkidslive,webkidstest,crmkidslive,crmkidstest"
        ),
        "vrgo.header.entitlementhash": "f0ef812776977c017c3521a79dd7054981d0584f",
    },
    "stage": {
        "base.url": "https://reqres.in/api",
        "vrgo.base.url": "https://api.vrgo.astro.stage.xp.irdeto.com",
        "vrgo.auth.token.url": "https://consumer-am.astro.stage.xp.irdeto.com/v1/auth/token",
        "vrgo.header.origin": "https://web.vrgo.astro.stage.xp.irdeto.com",
        "vrgo.header.referer": "https://web.vrgo.astro.stage.xp.irdeto.com/",
        "vrgo.header.catalogueids": (
            "ottlive,otttest,weblive,webtest,crmlive,crmtest,"
            "ottkidslive,ottkidstest,webkidslive,webkidstest,crmkidslive,crmkidstest"
        ),
        "vrgo.header.entitlementhash": "f0ef812776977c017c3521a79dd7054981d0584f",
    },
    "load": {
        "base.url": "https://reqres.in/api",
        "http.connect.timeout.ms": "15000",
        "http.read.timeout.ms": "60000",
        "vrgo.base.url": "https://api.vrgo.test.xp.irdeto.com",
    },
    "prod": {
        "base.url": "https://reqres.in/api",
        "vrgo.base.url": "https://api.vrptv.ctrp.astro.com.my",
        "vrgo.header.origin": "https://www.astro.com.my",
        "vrgo.header.referer": "https://www.astro.com.my/",
        "vrgo.header.catalogueids": "REPLACE_WITH_PROD_CATALOGUE_IDS",
        "vrgo.header.cp_id": "REPLACE",
        "vrgo.header.device_id": "REPLACE",
        "vrgo.header.entitlementhash": "REPLACE",
        "vrgo.header.entitlements": "[]",
        "vrgo.header.entitlementvalues": "REPLACE",
        "vrgo.header.profileid": "REPLACE",
        "vrgo.header.tenant_identifier": "REPLACE",
        "vrgo.header.user-agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ),
        "vrgo.x.api.key": "",
    },
}

HEADERS = {
    "test": "# VRGO test stack — canonical property keys and values for all environments.\n",
    "dev": "# VRGO dev stack — synced from test.properties; dev URLs and session headers.\n",
    "stage": "# VRGO Astro stage — synced from test.properties; stage URLs and session headers.\n",
    "load": "# Load profile — synced from test.properties; longer HTTP timeouts.\n",
    "prod": "# Production — synced from test.properties; set secrets via CI (never commit prod keys).\n",
}


def main() -> None:
    test = load_env("test")
    all_keys: set[str] = set(test)
    for env in ENVS:
        all_keys |= set(load_env(env))

    test_text = (ENV_DIR / "test.properties").read_text(encoding="utf-8")
    key_order: list[str] = []
    seen: set[str] = set()
    for line in test_text.splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key = line.partition("=")[0].strip()
            if key not in seen:
                key_order.append(key)
                seen.add(key)
    for key in sorted(all_keys - seen):
        key_order.append(key)

    sync_targets = [e for e in ENVS if e != "test"]

    for env in sync_targets:
        values = {k: test.get(k, "") for k in all_keys}
        values.update(OVERRIDES.get(env, {}))

        lines = [HEADERS[env]]
        for bootstrap in ("base.url", "http.connect.timeout.ms", "http.read.timeout.ms"):
            lines.append(f"{bootstrap}={values[bootstrap]}")
        lines.append("")

        for key in key_order:
            if key in ("base.url", "http.connect.timeout.ms", "http.read.timeout.ms"):
                continue
            lines.append(f"{key}={values[key]}")

        out_path = ENV_DIR / f"{env}.properties"
        out_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        print(f"{env}: {len(parse_props(out_path.read_text(encoding='utf-8')))} keys")

    # Ensure test.properties contains every key (append missing keys at end).
    test_path = ENV_DIR / "test.properties"
    test_lines = test_path.read_text(encoding="utf-8").splitlines()
    test_keys = set(parse_props("\n".join(test_lines)))
    missing_in_test = sorted(all_keys - test_keys)
    if missing_in_test:
        if test_lines and test_lines[-1].strip():
            test_lines.append("")
        test_lines.append("# --- Keys added by sync-env-properties.py ---")
        for key in missing_in_test:
            test_lines.append(f"{key}={test.get(key, '')}")
        test_path.write_text("\n".join(test_lines) + "\n", encoding="utf-8", newline="\n")
        test = load_env("test")

    sets = {e: set(load_env(e).keys()) for e in ENVS}
    union = set.union(*sets.values())
    ok = all(sets[e] == union for e in ENVS)
    print(f"Union key count: {len(union)} — parity {'OK' if ok else 'FAILED'}")
    if not ok:
        for e in ENVS:
            print(f"  {e} missing: {sorted(union - sets[e])}")


if __name__ == "__main__":
    main()
