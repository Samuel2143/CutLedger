"""Strict local configuration format: KEY=value, no shell evaluation."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENV = ROOT / '.env.local'

def read():
    values = {}
    if ENV.exists():
        for line in ENV.read_text().splitlines():
            if line and not line.startswith('#'):
                key, value = line.split('=', 1)
                values[key] = value
    return values

def save(values):
    temporary = ENV.with_suffix('.local.tmp')
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as output:
        for key, value in values.items():
            if '\n' in value or '\r' in value or '\x00' in value:
                raise ValueError('Configuration values must be single-line')
            output.write(f'{key}={value}\n')
    temporary.replace(ENV)
    ENV.chmod(0o600)
