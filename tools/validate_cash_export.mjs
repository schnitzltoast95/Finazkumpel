import fs from "node:fs/promises";
import path from "node:path";
import { Workbook } from "@oai/artifact-tool";

const root = process.cwd();
const csvPath = path.join(root, "tmp", "cash-export-validation", "transactions.csv");
const previewPath = path.join(root, "tmp", "cash-export-validation", "transactions-preview.png");
const csvText = (await fs.readFile(csvPath, "utf8")).replace(/^\uFEFF/, "");
const rows = csvText.trim().split(/\r?\n/).map((line) => line.split(";"));
const workbook = Workbook.create();
const sheet = workbook.worksheets.add("Transaktionen");
sheet.getRange("A1").write(rows);

sheet.showGridLines = false;
sheet.freezePanes.freezeRows(1);
sheet.getRange("A1:G4").format.font = { name: "Arial", size: 10 };
sheet.getRange("A1:G1").format = {
  fill: "#7A244A",
  font: { name: "Arial", size: 10, bold: true, color: "#FFFFFF" },
  verticalAlignment: "center",
};
sheet.getRange("A1:G4").format.autofitColumns();
sheet.getRange("A1:G4").format.autofitRows();
workbook.recalculate();

const inspected = await workbook.inspect({
  kind: "table",
  range: "Transaktionen!A1:G4",
  include: "values,formulas",
  tableMaxRows: 10,
  tableMaxCols: 10,
});
const preview = await workbook.render({
  sheetName: "Transaktionen",
  range: "A1:G4",
  scale: 2,
  format: "png",
});
await fs.writeFile(previewPath, new Uint8Array(await preview.arrayBuffer()));

const output = inspected.ndjson;
if (!output.includes("T-20261012-001") || !output.includes("39.00")) {
  throw new Error("CSV import did not preserve expected transaction values");
}
if ((output.match(/T-20261012-/g) || []).length < 3 || rows.some((row) => row.length !== 7)) {
  throw new Error("CSV import is missing transactions");
}
console.log("PASS: Artifact Tool imported, recalculated, inspected and rendered transactions.csv");
console.log(`PREVIEW=${previewPath}`);
