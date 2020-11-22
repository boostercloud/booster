#!/usr/bin/env sh

lerna clean --yes \
&& rm -rf node_modules \
&& lerna bootstrap \
&& npm install
