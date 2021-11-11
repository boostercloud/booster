#!/bin/bash
COLOR='\033[0;32m'
npm config set registry http://localhost:4873
publish(){
  declare -a packages=("${!1}")
  #for path in "${packages[@]}""
  #do
  #  echo -e “${COLOR} :hourglass_flowing_sand: Compiling $(basename “$path”) $(tput sgr0)”
  #  cd $path || exit
  #  yarn --silent compile || exit
  #  cd ../..
  #done
  for path in "${packages[@]}"
  do
    echo -e "${COLOR} :sparkles::mage::sparkles: Doing some magic to publish $(basename "$path") $(tput sgr0)"
    cd $path || exit
    npm unpublish --force --silent @boostercloud/$(basename "$path")
    npm publish --registry http://localhost:4873 --no-git-tag-version --canary --non-interactive
    cd ../..
  done
}
STARTTIME=$(date +%s)
lerna clean --yes
lerna bootstrap
lerna run clean --stream
lerna run compile --stream
PACKAGES+=(./packages/framework-types)
PACKAGES+=(./packages/framework-common-helpers)
PACKAGES+=(./packages/framework-core)
PACKAGES+=(./packages/framework-provider-aws)
PACKAGES+=(./packages/framework-provider-aws-infrastructure)
PACKAGES+=(./packages/framework-provider-local)
PACKAGES+=(./packages/framework-provider-local-infrastructure)
PACKAGES+=(./packages/framework-provider-azure)
PACKAGES+=(./packages/framework-provider-azure-infrastructure)
PACKAGES+=(./packages/framework-provider-kubernetes)
PACKAGES+=(./packages/framework-provider-kubernetes-infrastructure)
PACKAGES+=(./packages/cli)
publish PACKAGES[@]
echo -e "${COLOR} :firecracker:  Uninstalling the current Booster version $(tput sgr0)"
npm uninstall -g @boostercloud/cli
echo -e "${COLOR} :floppy_disk:  Installing the local Booster version including your magic  $(tput sgr0)"
npm install -g @boostercloud/cli
ENDTIME=$(date +%s)
echo -e "${COLOR} -----------------------"
echo -e "| :mage: Deploy time: $(($ENDTIME - $STARTTIME)) s |""
echo -e "" ----------------------- $(tput sgr0)"