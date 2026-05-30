#!/usr/bin/env node

let data = '';
process.stdin.on('data', chunk => (data += chunk));
process.stdin.on('end', () => {
  try {
    const toolInput = JSON.parse(data).tool_input || {};
    const subject = toolInput.file_path || toolInput.path || toolInput.command || '';

    if (/(?:^|[\\/])\.env(\.|$)/.test(subject) || /(?:^|[\\/])\.env$/.test(subject)) {
      const label = toolInput.file_path || toolInput.path || '.env';
      process.stderr.write(
        `BLOCKED: Access to '${label}' is not allowed. This file may contain sensitive credentials such as GEMINI_API_KEY or SLACK_WEBHOOK_URL.\n`
      );
      process.exit(2);
    }
  } catch {
    // unparseable input — allow through
  }
  process.exit(0);
});
