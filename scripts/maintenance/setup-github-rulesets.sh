#!/bin/bash

# GitHub Rulesets Setup Script
# Automates the creation of GitHub rulesets via GitHub CLI

set -e

# Configuration
REPO_OWNER="${GITHUB_OWNER:-$(git config --get remote.origin.url | sed -n 's#.*github\.com[:/]\([^/]*\)/.*#\1#p')}"
REPO_NAME="${GITHUB_REPO:-$(basename -s .git $(git config --get remote.origin.url))}"

echo "========================================="
echo "GITHUB RULESETS SETUP"
echo "Repository: $REPO_OWNER/$REPO_NAME"
echo "========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check prerequisites
check_prerequisites() {
    echo -e "${BLUE}Checking prerequisites...${NC}"
    
    # Check gh CLI
    if ! command -v gh &> /dev/null; then
        echo -e "${RED}✗ GitHub CLI (gh) is not installed${NC}"
        echo "Please install GitHub CLI: https://cli.github.com/"
        exit 1
    fi
    
    # Check authentication
    if ! gh auth status &> /dev/null; then
        echo -e "${RED}✗ GitHub CLI not authenticated${NC}"
        echo "Please authenticate: gh auth login"
        exit 1
    fi
    
    # Check repository access
    if ! gh repo view "$REPO_OWNER/$REPO_NAME" &> /dev/null; then
        echo -e "${RED}✗ Cannot access repository $REPO_OWNER/$REPO_NAME${NC}"
        echo "Please check repository name and permissions"
        exit 1
    fi
    
    echo -e "${GREEN}✓ All prerequisites met${NC}"
    echo ""
}

# Create production branch ruleset
create_production_ruleset() {
    echo -e "${BLUE}Creating Production Branch Ruleset...${NC}"
    
    # Create temporary JSON file for ruleset configuration
    cat > /tmp/production-ruleset.json <<EOF
{
  "name": "Production Protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/main", "refs/heads/master"],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "non_fast_forward"
    },
    {
      "type": "required_linear_history"
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 2,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": true,
        "require_last_push_approval": true,
        "required_review_thread_resolution": true
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": [
          {
            "context": "build-and-test",
            "integration_id": null
          },
          {
            "context": "security-scan",
            "integration_id": null
          },
          {
            "context": "lint-and-format",
            "integration_id": null
          },
          {
            "context": "code-quality",
            "integration_id": null
          }
        ],
        "strict_required_status_checks_policy": true
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_type": "RepositoryRole",
      "actor_id": 1,
      "bypass_mode": "always"
    }
  ]
}
EOF

    # Create the ruleset using GitHub API
    gh api \
        --method POST \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$REPO_OWNER/$REPO_NAME/rulesets" \
        --input /tmp/production-ruleset.json > /dev/null

    echo -e "${GREEN}✓ Production ruleset created${NC}"
    rm -f /tmp/production-ruleset.json
}

# Create development branch ruleset
create_development_ruleset() {
    echo -e "${BLUE}Creating Development Branch Ruleset...${NC}"
    
    cat > /tmp/development-ruleset.json <<EOF
{
  "name": "Development Protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/develop", "refs/heads/dev", "refs/heads/development"],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "non_fast_forward"
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 1,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": true,
        "required_review_thread_resolution": false
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": [
          {
            "context": "build-and-test",
            "integration_id": null
          },
          {
            "context": "lint-and-format",
            "integration_id": null
          }
        ],
        "strict_required_status_checks_policy": true
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_type": "RepositoryRole",
      "actor_id": 1,
      "bypass_mode": "always"
    }
  ]
}
EOF

    gh api \
        --method POST \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$REPO_OWNER/$REPO_NAME/rulesets" \
        --input /tmp/development-ruleset.json > /dev/null

    echo -e "${GREEN}✓ Development ruleset created${NC}"
    rm -f /tmp/development-ruleset.json
}

# Create feature branch ruleset
create_feature_ruleset() {
    echo -e "${BLUE}Creating Feature Branch Ruleset...${NC}"
    
    cat > /tmp/feature-ruleset.json <<EOF
{
  "name": "Feature Branch Guidelines",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": [
        "refs/heads/feature/*",
        "refs/heads/feat/*", 
        "refs/heads/fix/*",
        "refs/heads/bugfix/*"
      ],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": [
          {
            "context": "build-and-test",
            "integration_id": null
          },
          {
            "context": "lint-and-format",
            "integration_id": null
          }
        ],
        "strict_required_status_checks_policy": false
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_type": "RepositoryRole",
      "actor_id": 1,
      "bypass_mode": "always"
    }
  ]
}
EOF

    gh api \
        --method POST \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$REPO_OWNER/$REPO_NAME/rulesets" \
        --input /tmp/feature-ruleset.json > /dev/null

    echo -e "${GREEN}✓ Feature branch ruleset created${NC}"
    rm -f /tmp/feature-ruleset.json
}

# Create release branch ruleset
create_release_ruleset() {
    echo -e "${BLUE}Creating Release Branch Ruleset...${NC}"
    
    cat > /tmp/release-ruleset.json <<EOF
{
  "name": "Release Protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": [
        "refs/heads/release/*",
        "refs/heads/rel/*",
        "refs/heads/hotfix/*"
      ],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "non_fast_forward"
    },
    {
      "type": "required_linear_history"
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 2,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": true,
        "require_last_push_approval": true,
        "required_review_thread_resolution": true
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": [
          {
            "context": "build-and-test",
            "integration_id": null
          },
          {
            "context": "security-scan",
            "integration_id": null
          },
          {
            "context": "code-quality",
            "integration_id": null
          }
        ],
        "strict_required_status_checks_policy": true
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_type": "RepositoryRole",
      "actor_id": 1,
      "bypass_mode": "always"
    }
  ]
}
EOF

    gh api \
        --method POST \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$REPO_OWNER/$REPO_NAME/rulesets" \
        --input /tmp/release-ruleset.json > /dev/null

    echo -e "${GREEN}✓ Release branch ruleset created${NC}"
    rm -f /tmp/release-ruleset.json
}

# List existing rulesets
list_rulesets() {
    echo -e "${BLUE}Current Rulesets:${NC}"
    
    gh api \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$REPO_OWNER/$REPO_NAME/rulesets" \
        --jq '.[] | "  • \(.name) (\(.enforcement)) - \(.conditions.ref_name.include | join(\", \"))"'
    
    echo ""
}

# Interactive setup
interactive_setup() {
    echo -e "${YELLOW}Which rulesets would you like to create?${NC}"
    echo ""
    echo "1) Production only (main/master branch)"
    echo "2) Production + Development (main + develop branches)"
    echo "3) Full setup (all branch types)"
    echo "4) Custom selection"
    echo ""
    read -p "Enter your choice (1-4): " choice
    
    case $choice in
        1)
            create_production_ruleset
            ;;
        2)
            create_production_ruleset
            create_development_ruleset
            ;;
        3)
            create_production_ruleset
            create_development_ruleset
            create_feature_ruleset
            create_release_ruleset
            ;;
        4)
            echo ""
            read -p "Create production ruleset? (y/N): " prod
            [[ $prod =~ ^[Yy]$ ]] && create_production_ruleset
            
            read -p "Create development ruleset? (y/N): " dev
            [[ $dev =~ ^[Yy]$ ]] && create_development_ruleset
            
            read -p "Create feature branch ruleset? (y/N): " feat
            [[ $feat =~ ^[Yy]$ ]] && create_feature_ruleset
            
            read -p "Create release branch ruleset? (y/N): " rel
            [[ $rel =~ ^[Yy]$ ]] && create_release_ruleset
            ;;
        *)
            echo "Invalid choice. Exiting."
            exit 1
            ;;
    esac
}

# Show summary
show_summary() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}RULESETS SETUP COMPLETE${NC}"
    echo -e "${GREEN}=========================================${NC}"
    echo ""
    
    list_rulesets
    
    echo -e "${YELLOW}Next Steps:${NC}"
    echo "1. Review rulesets in GitHub repository settings"
    echo "2. Test with a pull request to verify status checks"
    echo "3. Train team members on new workflow"
    echo "4. Update team documentation"
    echo ""
    
    echo -e "${BLUE}GitHub Repository Settings:${NC}"
    echo "https://github.com/$REPO_OWNER/$REPO_NAME/settings/rulesets"
    echo ""
    
    echo -e "${BLUE}Status Checks Workflow:${NC}"
    echo "The status-checks.yml workflow will provide required checks:"
    echo "  • build-and-test"
    echo "  • lint-and-format"  
    echo "  • security-scan"
    echo "  • code-quality"
    echo ""
}

# Main execution
main() {
    check_prerequisites
    
    # Show current rulesets
    echo -e "${BLUE}Checking existing rulesets...${NC}"
    EXISTING_COUNT=$(gh api "/repos/$REPO_OWNER/$REPO_NAME/rulesets" --jq 'length')
    
    if [ "$EXISTING_COUNT" -gt 0 ]; then
        echo -e "${YELLOW}Found $EXISTING_COUNT existing ruleset(s):${NC}"
        list_rulesets
        echo -e "${YELLOW}This will add new rulesets. Continue? (y/N)${NC}"
        read -r response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            echo "Setup cancelled."
            exit 0
        fi
    fi
    
    interactive_setup
    show_summary
    
    echo -e "${GREEN}🎉 GitHub rulesets setup completed successfully!${NC}"
}

# Run main function
main