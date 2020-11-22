# In theory we could write all of this with Bash style &&,
# but in order to maintain backwards compatibility
# with Powershell <7.0 (what most Windows users have preinstalled)
# we write it in the old-school style
lerna clean --yes
if ($?) { rm -f packages/**/package-lock.json }
if ($?) { rm -rf node_modules }
if ($?) { rm package-lock.json }
if ($?) { lerna bootstrap }
if ($?) { npm install }
