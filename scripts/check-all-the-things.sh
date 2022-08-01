#!/usr/bin/env sh

npx lerna clean --yes \
&& npm run bootstrap \
&& npx lerna run clean --stream \
&& npx lerna run compile --stream \
&& npx lerna run format --stream \
&& npx lerna run lint:fix --stream \
&& npx lerna run lint:check --stream \
&& npx lerna run test --stream