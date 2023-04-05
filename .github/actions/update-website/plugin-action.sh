#!/bin/bash

# ===================================================================================================================================================================================== #
# Action Summary:                                                                                                                                                                       #
# ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- #
# - Gets an access token from Auth0 using the specified credentials to perform authorized HTTP requests                                                                                 #
# - Fetches a list of documents, which include the IDs, urls and checksums of the documents stored                                                                                      #
# - Uses the find command to iterate through each .md and .mdx file in the website directory and its subdirectories, excluding certain directories and fields                           #
# - Compares the local files and remote files to find missing files in each, and either stores or updates or deletes pages using HTTP requests depending on the comparison results.     #
# ===================================================================================================================================================================================== #

PROD_URL="https://booster-docs-chatgpt-plugin.herokuapp.com"
REPO_URL="https://raw.githubusercontent.com/boostercloud/booster/main/website/"
DOCS_URL="https://docs.boosterframework.com/"

# Auth0 credentials from the environment variables of the workflow file:
CLIENT_ID=$AUTH0_CLIENT_ID
CLIENT_SECRET=$AUTH0_CLIENT_SECRET
AUDIENCE=$AUTH0_CHATGPT_PLUGIN_AUDIENCE
AUTH0_DOMAIN=$AUTH0_DOMAIN

echo "Auth0 Domain: $AUTH0_DOMAIN"
echo "Current dir: $GITHUB_WORKSPACE"

if [ -d "$GITHUB_WORKSPACE/website/" ]; then
  cd "$GITHUB_WORKSPACE/website/"
else
  echo "Directory not found"
  exit 1
fi

# Get Auth0 token
auth0_response=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"client_id\":\"$CLIENT_ID\",\"client_secret\":\"$CLIENT_SECRET\",\"audience\":\"$AUDIENCE\",\"grant_type\":\"client_credentials\"}" \
  $AUTH0_DOMAIN)

access_token=$(echo $auth0_response | jq -r '.access_token')


# Check if access_token is empty or null and exit with an error if it is
if [ -z "$access_token" ] || [ "$access_token" = "null" ]; then
    echo "Error: Failed to obtain access token from Auth0."
    exit 1
fi

# Perform HTTP request using curl and store the result in a variable to get stored documents
documents_url="$PROD_URL/documents"
documents_result=$(curl -X GET -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}" $documents_url)

# Parse the results using jq
items=$(echo $documents_result | jq '.results')
printf 'Documents: %s' "$items"

remote_file_paths=()
remote_file_checksums=()
remote_file_ids=()

paths=()
filepaths=()
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
  url=$(_jq '.metadata.url')
  checksum=$(_jq '.metadata.checksum')

  echo "$url:$checksum"
  remote_file_ids+=("$id")
  remote_file_paths+=("$url")
  remote_file_checksums+=("$checksum")
done
echo "Remote Files Count: ${#remote_file_ids[@]} ========================="

# Define array of excluded files
excluded_files=("00_ai-assistant.md" "README.md")

# Define array of excluded directories
excluded_dirs=("node_modules" "build" ".docusaurus" "static" "src")

# Iterate through each .md and .mdx file in the current directory and its subdirectories
# Build the find command with excluded directories
find_cmd="find . -type f \( -name \"*.md\" -o -name \"*.mdx\" \)"

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
  filepaths+=($filepath)
  path="${filepath#./}"

  echo "FILE LOCATION: $filepath"
  echo "$REPO_URL$path:$checksum"
  paths+=("$REPO_URL$path")
  checksums+=("$checksum")
done < <(eval "$find_cmd")
echo "Local Files Count: ${#paths[@]} ========================="

stored_pages=()
removed_pages=()
ids_to_remove=()

echo "Find missing files in local files ==============="
# Find missing files in local files
for (( i=0; i<${#remote_file_ids[@]}; i++ )); do
  checksum="${remote_file_checksums[$i]}"
  
  # Check if the file exists in local_file and has the same checksum
  if grep -q -F "${checksum}" <(echo "${checksums[*]}"); then
    # The file exists in both arrays, so do nothing
  :
  else
    url="${remote_file_paths[$i]}"
    removed_pages+=("${url}:${checksum}")
    ids_to_remove+=("\"${remote_file_ids[$i]}\"")
  fi
done

if [ ${#ids_to_remove[@]} -ne 0 ]; then
  delete_url="$PROD_URL/delete"
  json_body=$(printf '{"ids": [%s]}' "$(IFS=','; echo "${ids_to_remove[*]}")")

  echo "Removing documents with id: ${json_body}"
  curl -X DELETE -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}" -d "$json_body" $delete_url
else
  echo "No remotes documents to be removed!"
fi


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


  path=$(dirname "$file")
  filename=$(basename "$file")    
    
  # Remove the prefix until "website/" or "docs/"
  last_folder=$(echo "$path" | sed -E 's/.*(website|docs)//')

  # Check if the last folder is "docs" or "website"
  if [ "$last_folder" == "" ]; then
    path=$filename
  else
    path="${last_folder:1}/$filename"
  fi

  # Apply the first regex: remove prefixes and suffixes
  result=$(echo "$path" | sed -E 's/^[\d_]+//;s/_([0-9]+\/)/\1/g')

  # Apply the second regex: remove digits and underscores
  result=$(echo "$result" | sed -E 's/[0-9]+_//g')

  # Remove extension from file
  path="${result%.*}"

  url="$DOCS_URL$path"
  
  if [ "$j" -ne -1 ] && [ "$url" == "${remote_file_paths[$j]}" ]; then
    # The file has the same checksum and path so nothing to do here
  :
  else
    title=$(basename "$path")

    if (( j == -1 )); then
      # The file does not exist in remote_file_checksums, so create a new page
      stored_pages+=("${file}:${checksum}")

      echo "Storing page with checksum: ${checksum}"
      filepath="${filepaths[$i]}"
      upsert_url="$PROD_URL/upsert"
      
      content=$(cat "$filepath")
      # Format the content as a JSON string
      content_json=$(echo "$content" | jq -Rs .)

      # Build the JSON body as a string
      json_body="{\"documents\":[{\"text\":$content_json,\"metadata\":{\"source\":\"file\",\"mimetype\":\"text/markdown\",\"checksum\":\"$checksum\",\"url\":\"$url\"}}]}"
    
      curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}" -d "$json_body" $upsert_url

    else
      echo "Updating document with id: ${document_id} to url: ${url}"
      document_id="${remote_file_ids[$j]}"
      update_url="$PROD_URL/update-url"
      json_body="{\"document_id\":\"$document_id\", \"new_url\":\"$url\"}"

      curl -X PATCH -H "Content-Type: application/json" -H "Authorization: Bearer ${access_token}" -d "$json_body" $update_url
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

