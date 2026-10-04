#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const readme = readFileSync(new URL("../README.md", import.meta.url), "utf8").trim();
const branch = execFileSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
  encoding: "utf8",
}).trim();
const root = execFileSync("git", ["rev-parse", "--show-toplevel"], {
  encoding: "utf8",
}).trim();

console.log(
  JSON.stringify(
    {
      project: "project-uno",
      readmeTitle: readme.split("\n")[0],
      gitRoot: root,
      branch,
      node: process.version,
      status: "ok",
    },
    null,
    2,
  ),
);
