# In theory we could write all of this with Bash style &&,
# but in order to maintain backwards compatibility
# with Powershell <7.0 (what most Windows users have preinstalled)
# we write it in the old-school style
lerna clean --yes
if ($?) { lerna bootstrap }
lerna run clean --stream
if ($?) { lerna run compile --stream }
if ($?) { lerna run lint:fix --stream }
if ($?) { lerna run lint:check --stream }
if ($?) { lerna run test --stream }