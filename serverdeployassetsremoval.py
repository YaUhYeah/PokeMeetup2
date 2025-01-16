import os
import re

# Paths to your assets and server source code
ASSETS_DIR = 'assets'
SERVER_SRC_DIR = 'core/'  # Adjust if your server code is elsewhere

# Collect all asset files
asset_files = []
for root, dirs, files in os.walk(ASSETS_DIR):
    for file in files:
        relative_path = os.path.relpath(os.path.join(root, file), ASSETS_DIR)
        asset_files.append(relative_path.replace('\\', '/'))

# Regex pattern to find asset references in code
asset_pattern = re.compile(r'["\']([^"\']+\.(png|jpg|jpeg|mp3|wav|ogg|atlas|json|txt))["\']')

# Collect all asset references in server code
asset_references = set()
for root, dirs, files in os.walk(SERVER_SRC_DIR):
    for file in files:
        if file.endswith('.java'):
            with open(os.path.join(root, file), 'r', encoding='utf-8') as f:
                content = f.read()
                matches = asset_pattern.findall(content)
                for match in matches:
                    asset_references.add(match)

# Identify unused assets
unused_assets = set(asset_files) - asset_references

# Output unused assets to a file
with open('unused_assets.txt', 'w') as f:
    for asset in sorted(unused_assets):
        f.write(asset + '\n')

print('Unused assets have been listed in unused_assets.txt')
