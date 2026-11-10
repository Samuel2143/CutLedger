# Sourced by local commands; no secrets, no system-wide installation.
cutledger_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -d "$cutledger_root/.local/tools/node-v24.22.2-linux-x64/bin" ]]; then
  export PATH="$cutledger_root/.local/tools/node-v24.22.2-linux-x64/bin:$PATH"
fi
if [[ -d "$cutledger_root/.local/tools/jdk-17.0.21+9" ]]; then
  export JAVA_HOME="$cutledger_root/.local/tools/jdk-17.0.21+9"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
