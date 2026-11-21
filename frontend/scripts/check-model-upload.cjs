const fs=require('node:fs'),ts=require('typescript'),assert=require('node:assert/strict'),path=require('node:path');
const out={};new Function('exports',ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/modelUpload.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText)(out);
const data={userHash:'u',dataSource:'weibo',posts:['a'],bertEmbeddings:[Array(768).fill(0.2)],emojiSequences:[[]]};
assert.equal(out.parseModelUpload(JSON.stringify(data)).posts.length,1);
for(const patch of [{dataSource:'unknown'},{bertEmbeddings:[[1]]},{emojiSequences:[]},{posts:Array(51).fill('x')},{userHash:''}])assert.throws(()=>out.parseModelUpload(JSON.stringify({...data,...patch})));
console.log('PASS: uploaded feature dimensions, alignment, source and count validation');
