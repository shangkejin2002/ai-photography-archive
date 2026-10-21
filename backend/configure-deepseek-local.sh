#!/bin/zsh
set -e

PROJECT_ROOT="${0:A:h:h}"
ENV_FILE="$PROJECT_ROOT/.env.local"

printf '请粘贴完整的 DeepSeek API Key（输入不会显示），然后按回车：\n> '
IFS= read -r -s DEEPSEEK_SECRET
printf '\n'

if [[ -z "$DEEPSEEK_SECRET" || "$DEEPSEEK_SECRET" != sk-* ]]; then
  unset DEEPSEEK_SECRET
  printf '没有保存：Key 应以 sk- 开头。请重新运行本脚本。\n'
  exit 1
fi

umask 077
{
  printf 'DEEPSEEK_API_KEY=%s\n' "$DEEPSEEK_SECRET"
  printf 'DEEPSEEK_BASE_URL=https://api.deepseek.com\n'
  printf 'DEEPSEEK_MODEL=deepseek-flash\n'
  printf 'DEEPSEEK_TIMEOUT_SECONDS=60\n'
} > "$ENV_FILE"
chmod 600 "$ENV_FILE"
unset DEEPSEEK_SECRET

printf '配置已安全保存到 .env.local。现在可以重启后端并验证 Stage 5。\n'
