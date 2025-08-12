#!/usr/bin/env python3
import yaml
import sys

# Read the YAML file
with open('src/main/resources/application.yml', 'r') as f:
    content = f.read()

# Split by profiles
profiles = content.split('\n---\n')

def merge_app_sections(profile_content):
    """Merge multiple app sections in a profile"""
    lines = profile_content.split('\n')
    result = []
    app_sections = []
    current_app = []
    in_app = False
    indent_level = 0
    
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # Check if this is an app: line at root level
        if line.strip() == 'app:' and (not line.startswith(' ') or line[:4] == 'app:'):
            if in_app and current_app:
                # Save previous app section
                app_sections.append('\n'.join(current_app[1:]))  # Skip the app: line
            in_app = True
            current_app = [line]
            indent_level = len(line) - len(line.lstrip())
        elif in_app:
            # Check if we're still in the app section
            if line.strip() and not line.startswith(' ' * (indent_level + 1)) and line.strip() != '':
                # We've left the app section
                if current_app:
                    app_sections.append('\n'.join(current_app[1:]))  # Skip the app: line
                in_app = False
                current_app = []
                result.append(line)
            else:
                current_app.append(line)
        else:
            result.append(line)
        
        i += 1
    
    # Handle last app section
    if in_app and current_app:
        app_sections.append('\n'.join(current_app[1:]))
    
    # If we have app sections, merge them and add at the end
    if app_sections:
        merged_app = 'app:\n' + '\n'.join(app_sections)
        result.append(merged_app)
    
    return '\n'.join(result)

# Process each profile
fixed_profiles = []
for profile in profiles:
    fixed_profile = merge_app_sections(profile)
    fixed_profiles.append(fixed_profile)

# Join profiles back
fixed_content = '\n---\n'.join(fixed_profiles)

# Write the fixed content
with open('src/main/resources/application-fixed.yml', 'w') as f:
    f.write(fixed_content)

print("Fixed YAML written to application-fixed.yml")