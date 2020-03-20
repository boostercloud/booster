git clone https://github.com/boostercloud/docs-site .docs
cp -r ./docs/documentation/* ./.docs/source/includes/
Set-Location ./docs
bundle install
bundle exec middleman build --clean
Set-Location ..