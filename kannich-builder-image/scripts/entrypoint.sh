#!/bin/bash
# Kannich builder entrypoint script
# Sets up the build environment

set -e

# Ensure cache directory exists and has correct permissions
mkdir -p /kannich/cache
chmod 755 /kannich/cache

# Ensure overlay directories exist
mkdir -p /kannich/overlays
chmod 755 /kannich/overlays

# If a command was passed, execute it
if [ $# -gt 0 ]; then
    exec "$@"
fi

# Otherwise keep container running
exec tail -f /dev/null
