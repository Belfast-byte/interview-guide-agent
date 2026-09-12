#!/usr/bin/env python3
"""Run V20261007 checks in a disposable native PostgreSQL cluster (60-second deadline).

Example for an extracted Ubuntu PostgreSQL package:
LD_LIBRARY_PATH=/tmp/pg/usr/lib/x86_64-linux-gnu python3 scripts/verification/verify_code_repair_migration.py \
  --bin-dir /tmp/pg/usr/lib/postgresql/12/bin --share-dir /tmp/pg/usr/share/postgresql/12

No existing database, credentials, TCP port, or application data is used.
"""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

TEST_TIMEOUT_SECONDS = 60
STOP_TIMEOUT_SECONDS = 5


def run(command, *, deadline, env):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise TimeoutError("PostgreSQL migration verification exceeded 60 seconds")
    subprocess.run(command, check=True, env=env, timeout=remaining)


def verify(options):
    deadline = time.monotonic() + TEST_TIMEOUT_SECONDS - STOP_TIMEOUT_SECONDS
    binaries = Path(options.bin_dir).resolve()
    cluster = Path(tempfile.mkdtemp(prefix="code-repair-postgres-"))
    data = cluster / "data"
    env = {key: value for key, value in os.environ.items() if not key.startswith("PG")}
    started = False
    try:
        init = [str(binaries / "initdb"), "-D", str(data), "-A", "trust", "--no-locale", "-E", "UTF8"]
        if options.share_dir:
            init.extend(["-L", str(Path(options.share_dir).resolve())])
        run(init, deadline=deadline, env=env)
        run([str(binaries / "pg_ctl"), "-D", str(data), "-l", str(cluster / "server.log"),
             "-o", f"-F -c listen_addresses='' -k {cluster}", "-w", "start"], deadline=deadline, env=env)
        started = True
        run([str(binaries / "psql"), "-X", "-h", str(cluster), "-d", "postgres", "-v", "ON_ERROR_STOP=1",
             "-f", str(Path(__file__).with_name("code_repair_migration.sql").resolve())], deadline=deadline, env=env)
    except Exception:
        log = cluster / "server.log"
        if log.exists():
            print(log.read_text(), flush=True)
        raise
    finally:
        if started or (data / "postmaster.pid").exists():
            subprocess.run([str(binaries / "pg_ctl"), "-D", str(data), "-m", "immediate", "-w", "stop"],
                           check=True, env=env, timeout=STOP_TIMEOUT_SECONDS)
        shutil.rmtree(cluster)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bin-dir", required=True, help="Directory containing initdb, pg_ctl and psql")
    parser.add_argument("--share-dir", help="PostgreSQL shared data directory for unpacked packages")
    verify(parser.parse_args())


if __name__ == "__main__":
    main()
