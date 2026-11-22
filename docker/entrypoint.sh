#!/bin/sh
# Container entrypoint for the shell server.
#
# - With no arguments, or only options (e.g. "--port 2705"), runs "serve".
# - Any other subcommand is passed through: "useradd bob", "users", "userdel bob", ...
# - When SHELL_USER and SHELL_PASSWORD are set and the account does not exist yet,
#   it is created before the server starts. The password never appears in argv.
set -eu

JAR=/app/shell-server.jar

if [ "$#" -eq 0 ] || [ "${1#-}" != "$1" ]; then
    set -- serve "$@"
fi

if [ "$1" = "serve" ] && [ -n "${SHELL_USER:-}" ] && [ -n "${SHELL_PASSWORD:-}" ]; then
    if ! java -jar "$JAR" users | grep -qxF "$SHELL_USER"; then
        printf '%s\n' "$SHELL_PASSWORD" | java -jar "$JAR" useradd "$SHELL_USER" --password-stdin
    fi
fi
unset SHELL_PASSWORD

# shellcheck disable=SC2086 # JAVA_OPTS is intentionally word-split
exec java ${JAVA_OPTS:-} -jar "$JAR" "$@"
