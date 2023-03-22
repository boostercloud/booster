#!/bin/bash

GRAPHQL_URL="http://localhost:3000/graphql"
REPO_URL="https://raw.githubusercontent.com/boostercloud/booster/main/"

# Auth0 credentials from environment variables
CLIENT_ID="$AUTH0_CLIENT_ID"
CLIENT_SECRET="$AUTH0_CLIENT_SECRET"
AUDIENCE="$AUTH0_PRIVATEGPT_AUDIENCE"
AUTH0_DOMAIN="$AUTH0_DOMAIN"

# Go up three directories to get to the parent's parent's parent
cd ../../..

# Get Auth0 token
auth0_response=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"client_id\":\"$CLIENT_ID\",\"client_secret\":\"$CLIENT_SECRET\",\"audience\":\"$AUDIENCE\",\"grant_type\":\"client_credentials\"}" \
  $AUTH0_DOMAIN)

access_token=$(echo $auth0_response | jq -r '.access_token')

# Define GraphQL query
fetch_pages_query='{ListPageReadModels(filter: {}, limit: 1000, sortBy: {}) {items {id path title checksum} count cursor}}'

# Perform the GraphQL query using curl and store the result in a variable
graphql_result=$(curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}" \
  -d "{\"query\": \"$fetch_pages_query\"}" $GRAPHQL_URL)

# For debugging purposes to print the result of the query
# printf '%s' "$graphql_result" | jq '.data.ListPageReadModels.items[] | {id, path, title, checksum}'

# Parse the GraphQL result using jq
items=$(echo $graphql_result | jq '.data.ListPageReadModels.items')
count=$(echo $graphql_result | jq '.data.ListPageReadModels.count')
cursor=$(echo $graphql_result | jq '.data.ListPageReadModels.cursor')


remote_file_paths=()
remote_file_checksums=()
remote_file_ids=()

paths=()
checksums=()


echo "Remote Files ========================="

# Loop over each item in the 'items' array
for item in $(echo "${items}" | jq -r '.[] | @base64'); do
  # Decode the base64-encoded JSON for the current item
  _jq() {
    echo "${item}" | base64 --decode | jq -r "${1}"
  }

  # Extract the fields from the current item
  id=$(_jq '.id')
  path=$(_jq '.path')
  title=$(_jq '.title')
  checksum=$(_jq '.checksum')

  echo "$path/$title:$checksum"
  remote_file_ids+=("$id")
  remote_file_paths+=("$path/$title")
  remote_file_checksums+=("$checksum")
done
echo "Remote Files Count: ${#remote_file_ids[@]} ========================="

# Define array of excluded files
excluded_files=("00_ai-assistant.md" "README.md")

# Define array of excluded directories
excluded_dirs=("node_modules" "build" ".docusaurus" "static" "src")

# Iterate through each .md and .mdx file in the website directory and its subdirectories
# Build the find command with excluded directories
find_cmd="find website -type f \( -name \"*.md\" -o -name \"*.mdx\" \)"

for dir in "${excluded_dirs[@]}"; do
  find_cmd+=" -not -path \"*/$dir/*\""
done

for file in "${excluded_files[@]}"; do
  find_cmd+=" -not -name \"$file\""
done

echo "Local Files ========================="

# Use process substitution to avoid creating a subshell
while read filepath; do
  checksum=$(openssl sha256 < "$filepath" | cut -d ' ' -f 2)

  echo "$REPO_URL$filepath:$checksum"
  paths+=("$REPO_URL$filepath")
  checksums+=("$checksum")
done < <(eval "$find_cmd")
echo "Local Files Count: ${#paths[@]} ========================="

stored_pages=()
removed_pages=()

echo "Find missing files in local files ==============="
# Find missing files in local files
for (( i=0; i<${#remote_file_paths[@]}; i++ )); do
  file="${remote_file_paths[$i]}"
  checksum="${remote_file_checksums[$i]}"
  
  # Check if the file exists in local_file and has the same checksum
  if grep -q -F "${checksum}" <(echo "${checksums[*]}"); then
    # The file exists in both arrays, so do nothing
  :
  else
    removed_pages+=("${file}:${checksum}")

    page_id="${remote_file_ids[$i]}"
    echo "Removing page with id: ${page_id}"

    curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}"\
  -d "{\"query\": \"mutation { DeletePage(input: { pageID: \\\"$page_id\\\" }) }\"}" \
  $GRAPHQL_URL
  fi
done

echo "Find missing files in remote files ==============="
# Find missing files in remote files
for (( i=0; i<${#paths[@]}; i++ )); do
  file="${paths[$i]}"
  checksum="${checksums[$i]}"
  

  # Check if the file exists in remote_file and has the same checksum
  j=-1
  for (( k=0; k<${#remote_file_checksums[@]}; k++ )); do
    if [[ "${remote_file_checksums[$k]}" == "${checksum}" ]]; then
      j=$k
      break
    fi
  done


  if (( j == -1 )); then
    # The file does not exist in remote_file_checksums, so create a new page
    stored_pages+=("${file}:${checksum}")

    echo "Storing page with checksum: ${checksum}"
    path=$(dirname "$file")
    filename=$(basename "$file")

    curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}"\
    -d "{\"query\": \"mutation { StorePage(input: { path: \\\"$path\\\", title: \\\"$filename\\\", checksum: \\\"$checksum\\\" }) }\"}" \
    $GRAPHQL_URL

  else
    remote_file="${remote_file_paths[$j]}"
    
    if [[ "${file}" == "${remote_file}" ]]; then
      # The file has the same checksum and path, so nothing to do here
    :
    else
      page_id="${remote_file_ids[$j]}"
      path=$(dirname "$file")
      filename=$(basename "$file")

      echo "Updating page with id: ${page_id} to path: ${path} name: ${filename} checksum: ${checksum}"

      curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}"\
      -d "{\"query\": \"mutation { UpdatePage(input: { pageID: \\\"$page_id\\\", path: \\\"$path\\\", title: \\\"$filename\\\" }) }\"}" \
      $GRAPHQL_URL
    fi
  fi
done


# Print the results
echo "Local files added:"
echo "${#stored_pages[@]}"

for (( i=0; i<${#stored_pages[@]}; i++ )); do
  echo -e "${stored_pages[$i]}"
done

echo -e "Remote files deleted:"
echo "${#removed_pages[@]}"

for (( i=0; i<${#removed_pages[@]}; i++ )); do
  echo -e "${removed_pages[$i]}"
done

