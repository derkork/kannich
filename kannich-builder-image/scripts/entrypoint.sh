#!/bin/bash
# Kannich builder entrypoint script

if [ $# -gt 0 ]; then
    # if a command was passed run kannich with
    # the arguments
    /kannich/jdk/bin/java -jar /kannich/kannich-cli.jar $@
    exit $?
fi

echo "Usage"
echo "docker run derkork/kannich:<version>  [...]"
exit 1