#!/bin/bash

# GitHub Push Script for Oddiya Backend
# Repository: https://github.com/swm16-wREck/Oddiya-Backend.git

echo "======================================"
echo "GitHub Push Script for Oddiya Backend"
echo "======================================"
echo ""

# Check if we have commits
if [ $(git rev-list --count HEAD) -eq 0 ]; then
    echo "❌ No commits found. Please commit your code first."
    exit 1
fi

echo "📋 Current Status:"
echo "Branch: $(git branch --show-current)"
echo "Remote: $(git remote get-url origin)"
echo "Last commit: $(git log -1 --oneline)"
echo ""

echo "Choose authentication method:"
echo "1) Personal Access Token (Recommended)"
echo "2) SSH Key"
echo "3) Exit"
echo ""
read -p "Enter your choice (1-3): " choice

case $choice in
    1)
        echo ""
        echo "📝 Personal Access Token Method"
        echo "--------------------------------"
        echo "1. Go to: https://github.com/settings/tokens"
        echo "2. Click 'Generate new token (classic)'"
        echo "3. Name: 'Oddiya Backend Push'"
        echo "4. Select scope: 'repo' (full control)"
        echo "5. Generate and copy the token"
        echo ""
        read -p "Enter your GitHub username (sw6820): " username
        username=${username:-sw6820}
        echo "Enter your Personal Access Token (hidden): "
        read -s token
        echo ""
        
        if [ -z "$token" ]; then
            echo "❌ Token cannot be empty"
            exit 1
        fi
        
        echo "🚀 Pushing to GitHub..."
        git push https://${username}:${token}@github.com/swm16-wREck/Oddiya-Backend.git main
        
        if [ $? -eq 0 ]; then
            echo "✅ Successfully pushed to GitHub!"
            echo "🔗 View your repository: https://github.com/swm16-wREck/Oddiya-Backend"
        else
            echo "❌ Push failed. Please check your token and try again."
        fi
        ;;
        
    2)
        echo ""
        echo "🔑 SSH Key Method"
        echo "------------------"
        
        # Check if SSH key exists
        if [ ! -f ~/.ssh/id_ed25519 ] && [ ! -f ~/.ssh/id_rsa ]; then
            echo "No SSH key found. Generating new key..."
            read -p "Enter your email: " email
            ssh-keygen -t ed25519 -C "$email" -f ~/.ssh/id_ed25519 -N ""
            echo ""
            echo "📋 Copy this SSH key and add it to GitHub (https://github.com/settings/keys):"
            echo ""
            cat ~/.ssh/id_ed25519.pub
            echo ""
            read -p "Press Enter after adding the key to GitHub..."
        fi
        
        echo "🔄 Changing remote to SSH..."
        git remote set-url origin git@github.com:swm16-wREck/Oddiya-Backend.git
        
        echo "🚀 Pushing to GitHub..."
        git push -u origin main
        
        if [ $? -eq 0 ]; then
            echo "✅ Successfully pushed to GitHub!"
            echo "🔗 View your repository: https://github.com/swm16-wREck/Oddiya-Backend"
        else
            echo "❌ Push failed. Please check your SSH key configuration."
        fi
        ;;
        
    3)
        echo "Exiting..."
        exit 0
        ;;
        
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo ""
echo "======================================"
echo "Next Steps After Push:"
echo "======================================"
echo "1. Configure GitHub Secrets in repository settings:"
echo "   - AWS_ACCESS_KEY_ID"
echo "   - AWS_SECRET_ACCESS_KEY"
echo ""
echo "2. Create ECR repository:"
echo "   aws ecr create-repository --repository-name oddiya --region ap-northeast-2"
echo ""
echo "3. Check GitHub Actions:"
echo "   https://github.com/swm16-wREck/Oddiya-Backend/actions"
echo ""