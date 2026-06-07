#!/usr/bin/env bash
# push-to-github.sh
# 一键推送 PrivateVault 到 GitHub
# 用法:
#   1. 在 GitHub 上创建空仓库 YOUR_USERNAME/PrivateVault (不要勾 README/license/.gitignore)
#   2. 设置环境变量 GITHUB_USERNAME
#   3. 运行: ./push-to-github.sh

set -euo pipefail

REPO_NAME="${REPO_NAME:-PrivateVault}"
GITHUB_USERNAME="${GITHUB_USERNAME:-YOUR_GITHUB_USERNAME}"
VISIBILITY="${VISIBILITY:-public}"  # or private

if [[ "$GITHUB_USERNAME" == "YOUR_GITHUB_USERNAME" ]]; then
    echo "请设置 GITHUB_USERNAME 环境变量:"
    echo "  export GITHUB_USERNAME=<your-github-username>"
    echo "  ./push-to-github.sh"
    exit 1
fi

cd "$(dirname "$0")"

echo "==> 检查 git 状态"
if [[ -n "$(git status --porcelain)" ]]; then
    echo "有未提交修改:"
    git status
    read -p "是否继续? [y/N] " ans
    [[ "$ans" =~ ^[Yy]$ ]] || exit 1
fi

echo "==> 添加 remote"
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/${GITHUB_USERNAME}/${REPO_NAME}.git"

echo "==> 推送到 main"
git push -u origin main

echo ""
echo "✓ 推送完成!"
echo "  https://github.com/${GITHUB_USERNAME}/${REPO_NAME}"
echo ""
echo "下一步建议:"
echo "  1. 在 GitHub Settings → General 设置 Description / Website"
echo "  2. 在 Settings → Code security 启用 Dependabot alerts"
echo "  3. 在 Settings → Pages 启用 GitHub Pages (source: /docs)"
echo "  4. 在 Settings → Security 启用 Private vulnerability reporting"
echo "  5. 替换 README.md / docs/ 中所有 YOUR_GITHUB_USERNAME 占位符"
