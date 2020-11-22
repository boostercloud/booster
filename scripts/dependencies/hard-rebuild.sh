#!/usr/bin/env sh

lerna clean --yes \
&& rm -f packages/**/package-lock.json \
&& rm -rf node_modules \
&& rm package-lock.json \
&& lerna bootstrap \
&& npm install
