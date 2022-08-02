# WARNING: This script is unmaintained, use at your own risk
#
# In theory we could write all of this with Bash style &&,
# but in order to maintain backwards compatibility
# with Powershell <7.0 (what most Windows users have preinstalled)
# we write it in the old-school style
npx lerna clean --yes
if ($?) { npm run bootstrap }
npx lerna run clean --stream
if ($?) { npx lerna run build --stream }
if ($?) { npx lerna run format --stream }
if ($?) { npx lerna run lint:fix --stream }
if ($?) { npx lerna run lint:check --stream }
if ($?) { npx lerna run test --stream }