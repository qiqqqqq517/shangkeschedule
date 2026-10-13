const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('DLMU 按 teachers 声明分隔相邻课程，保留各自节次与星期', () => {
  const file = path.resolve(__dirname, '../../shared/assets/offline_repo/schools/resources/DLMU/dlmu_01.js');
  const source = fs.readFileSync(file, 'utf8').replace(/\brun\(\);\s*$/, '');
  for (const space of [' ', '\t', '  ']) {
    const sample = `<script>
var${space}teachers = [{id:1,name:'甲老师'}];
activity = new TaskActivity(1,2,3,'课程甲',5,'A101','011');
index = 0 * unitCount + 0;
var${space}teachers = [{id:2,name:'乙老师'}];
activity = new TaskActivity(1,2,3,'课程乙',5,'B101','011');
index = 1 * unitCount + 4;
</script>`;
    const context = vm.createContext({ console: { warn() {}, error() {}, log() {} }, sample });
    vm.runInContext(source, context);
    const courses = JSON.parse(JSON.stringify(vm.runInContext('parseCourses(sample)', context)));
    assert.equal(courses.length, 2);
    assert.equal(courses[0].name, '课程甲');
    assert.equal(courses[0].endSection, 1);
    assert.equal(courses[1].name, '课程乙');
    assert.equal(courses[1].day, 2);
    assert.equal(courses[1].startSection, 5);
  }
});
