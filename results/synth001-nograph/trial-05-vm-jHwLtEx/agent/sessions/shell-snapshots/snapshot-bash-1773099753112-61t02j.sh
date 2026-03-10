# Snapshot file
# Unset all aliases to avoid conflicts with functions
unalias -a 2>/dev/null || true
shopt -s expand_aliases
# Check for rg availability
if ! (unalias rg 2>/dev/null; command -v rg) >/dev/null 2>&1; then
  function rg {
  if [[ -n $ZSH_VERSION ]]; then
    ARGV0=rg /root/.local/share/claude/versions/2.1.71 "$@"
  elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ "$OSTYPE" == "win32" ]]; then
    ARGV0=rg /root/.local/share/claude/versions/2.1.71 "$@"
  elif [[ $BASHPID != $$ ]]; then
    exec -a rg /root/.local/share/claude/versions/2.1.71 "$@"
  else
    (exec -a rg /root/.local/share/claude/versions/2.1.71 "$@")
  fi
}
fi
export PATH=/root/.local/bin\:/opt/fray/bin\:/usr/local/sbin\:/usr/local/bin\:/usr/sbin\:/usr/bin\:/sbin\:/bin
