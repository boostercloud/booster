#!/usr/bin/env sh

rush clean \
&& rush purge \
&& rush update \
&& rush rebuild \
&& rush lint:fix \
&& rush test
