git clone https://github.com/boostercloud/docs-site .docs
Copy-Item -r .\docs\documentation\* .\.docs\source\includes\
Set-Location .\docs
bundle exec middleman build --clean
Copy-Item -r .\build ..
Set-Location ..