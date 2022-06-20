#!/usr/bin/env sh

LERNA="npx lerna"

$LERNA clean --yes \
&& $LERNA bootstrap \
&& $LERNA run clean --stream \
&& $LERNA run compile --stream \
&& $LERNA run format --stream \
&& $LERNA run lint:fix --stream \
&& $LERNA run lint:check --stream \
&& $LERNA run test --stream