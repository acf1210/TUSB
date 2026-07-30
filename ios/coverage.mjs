import fs from "node:fs";

const report = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const files = report.data[0].files.filter(({ filename }) =>
  filename.includes("/Sources/TUSBCore/"),
);
const totals = files.reduce(
  (sum, file) => ({
    covered: sum.covered + file.summary.lines.covered,
    count: sum.count + file.summary.lines.count,
  }),
  { covered: 0, count: 0 },
);
const percent = (100 * totals.covered) / totals.count;

console.log(`TUSBCore line coverage: ${percent.toFixed(1)}% (${totals.covered}/${totals.count})`);
process.exit(percent >= 80 ? 0 : 1);
