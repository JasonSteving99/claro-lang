const assert = require('assert');
const fs = require('fs');

// Make sure there's a file like tools/clarodocs/dist/assets/index.12345678.js
const files = fs.readdirSync('tools/clarodocs/dist/assets');
console.log(files);
assert.ok(files.some((f) => /index\.[0-9a-f]{8}\.js/.test(f)));
