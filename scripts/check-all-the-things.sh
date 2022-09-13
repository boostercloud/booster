#!/usr/bin/env sh

rush purge \
&& rush install \
&& rush build \
&& rush lint:fix \
&& rush test